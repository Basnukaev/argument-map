package ru.basnukaev.argumentmap.hadith.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.hadith.config.MatnTranslateRateLimitProperties;
import ru.basnukaev.argumentmap.hadith.web.MatnTranslateRateLimitExceededException;

/**
 * In-memory per-user sliding-window cost-guard на AI-перевод матна (P2-3).
 *
 * <p>{@link #acquire(UUID)} вызывается в {@code HadithTranslationService.translate}
 * НЕПОСРЕДСТВЕННО перед платным LLM-вызовом — после cache-чека, force-ADMIN
 * guard'а и isEnabled guard'а. Поэтому бюджет тратят ТОЛЬКО запросы, реально
 * идущие в модель: повторный перевод из кэша ({@code cached=true}), 403/404/422/503
 * лимит не расходуют. При превышении {@link MatnTranslateRateLimitProperties#requestsPerWindow}
 * запросов в окне — бросает {@link MatnTranslateRateLimitExceededException} (429).
 *
 * <p>Паттерн идентичен {@code BookViewDedupService} / {@code RateLimitFilter}:
 * тот же {@link Clock} inject (тесты fast-forward без sleep), то же lazy
 * cleanup через счётчик. Состояние per-user в {@link ConcurrentHashMap},
 * мутации под monitor на самом {@code UserState} (один lock per user).
 *
 * <p>Ключ — {@code userId} ({@code @CurrentUser}), не IP: endpoint
 * аутентифицированный, per-user корректнее (один пользователь за NAT не
 * блокирует других; смена IP не обходит лимит). Anonymous сюда не доходит —
 * резолвер {@code @CurrentUser} отсекает (401) до сервиса.
 */
@Component
@EnableConfigurationProperties(MatnTranslateRateLimitProperties.class)
public class MatnTranslateRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(MatnTranslateRateLimiter.class);

    /** Раз в N вызовов запускаем lazy eviction stale entries. */
    private static final int CLEANUP_EVERY = 256;

    /** Idle threshold для cleanup — entry без активности дольше окна evict-ится. */
    private static final Duration IDLE_EVICT = Duration.ofHours(1);

    private final MatnTranslateRateLimitProperties properties;
    private final Clock clock;

    /** Per-user state. Key — userId. Value — окно timestamp'ов LLM-вызовов. */
    private final ConcurrentHashMap<UUID, UserState> stateByUser = new ConcurrentHashMap<>();

    private final AtomicInteger callCount = new AtomicInteger(0);

    public MatnTranslateRateLimiter(MatnTranslateRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Регистрирует один LLM-bound translate-запрос пользователя и проверяет
     * лимит. No-op (просто возвращается) если лимит отключён. При превышении
     * {@link MatnTranslateRateLimitProperties#requestsPerWindow} в окне —
     * бросает {@link MatnTranslateRateLimitExceededException} (429), НЕ
     * регистрируя текущий запрос (отклонённый вызов модели не делается, его
     * не считаем в окно).
     *
     * @param userId текущий пользователь ({@code @CurrentUser})
     * @throws MatnTranslateRateLimitExceededException 429 — лимит исчерпан
     */
    public void acquire(UUID userId) {
        if (!properties.enabled()) {
            return;
        }

        Instant now = clock.instant();
        Instant windowStart = now.minus(properties.window());
        int limit = properties.requestsPerWindow();
        UserState state = stateByUser.computeIfAbsent(userId, k -> new UserState());

        synchronized (state) {
            // Evict timestamps старее окна
            while (!state.calls.isEmpty() && state.calls.peekFirst().isBefore(windowStart)) {
                state.calls.pollFirst();
            }
            state.lastSeen = now;
            if (state.calls.size() >= limit) {
                long retryAfter = retryAfterSeconds(state.calls.peekFirst(), now);
                log.warn("Лимит AI-перевода исчерпан: userId={}, окно={}, лимит={}, retryAfterSec={}",
                        userId, properties.window(), limit, retryAfter);
                maybeCleanup(now);
                throw new MatnTranslateRateLimitExceededException(limit, retryAfter);
            }
            state.calls.addLast(now);
        }

        maybeCleanup(now);
    }

    /**
     * Сколько секунд до освобождения слота — пока самый старый запрос в окне
     * не «вытечет» за {@code window}. Минимум 1 секунда (Retry-After=0
     * бессмысленен).
     */
    private long retryAfterSeconds(Instant oldest, Instant now) {
        Instant freeAt = oldest.plus(properties.window());
        return Math.max(1, Duration.between(now, freeAt).toSeconds());
    }

    /**
     * Lazy eviction idle-пользователей. Вызывается не на каждом вызове —
     * раз в {@link #CLEANUP_EVERY}. Evict только если окно пустое/протухло
     * И lastSeen старше {@link #IDLE_EVICT}.
     */
    private void maybeCleanup(Instant now) {
        if (callCount.incrementAndGet() < CLEANUP_EVERY) {
            return;
        }
        callCount.set(0);
        Instant evictBefore = now.minus(IDLE_EVICT);
        Iterator<Map.Entry<UUID, UserState>> it = stateByUser.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UserState> e = it.next();
            UserState s = e.getValue();
            synchronized (s) {
                if (s.lastSeen == null || s.lastSeen.isBefore(evictBefore)) {
                    it.remove();
                }
            }
        }
    }

    /** Для тестов: принудительная очистка без ожидания threshold. */
    void cleanupNow() {
        callCount.set(CLEANUP_EVERY - 1);
        maybeCleanup(clock.instant());
    }

    /** Для тестов: число активных пользователей в окне. */
    int stateSize() {
        return stateByUser.size();
    }

    /** Для тестов: сброс состояния между тестами в одном Spring context. */
    void resetState() {
        stateByUser.clear();
        callCount.set(0);
    }

    /** Per-user окно timestamp'ов. Мутации под {@code synchronized (state)}. */
    private static final class UserState {
        /** Timestamp'ы LLM-вызовов в окне. Сортирован ascending. */
        final Deque<Instant> calls = new ArrayDeque<>();
        /** Время последнего обращения — для idle cleanup. */
        Instant lastSeen;
    }
}
