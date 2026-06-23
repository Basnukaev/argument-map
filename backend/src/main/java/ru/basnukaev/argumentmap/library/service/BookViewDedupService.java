package ru.basnukaev.argumentmap.library.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.library.config.BookViewDedupProperties;

/**
 * In-memory sliding-window дедупликатор инкрементов просмотров книг.
 *
 * <p>Гарантирует, что один (clientIp + bookId) засчитывается в счётчик
 * не чаще одного раза в {@link BookViewDedupProperties#dedupWindow()}. Если
 * пара уже была зарегистрирована — {@link #shouldIncrement} возвращает
 * {@code false} и сервис не делает UPDATE в БД. Клиент всегда получает
 * 204, утечки состояния дедупа нет.
 *
 * <p>Состояние хранится в {@link ConcurrentHashMap}. Запись evict-ится
 * как только окно истекает — lazy cleanup срабатывает раз в
 * {@link #CLEANUP_EVERY} вызовов.
 *
 * <p>Размер Map ограничен числом уникальных (ip+bookId) пар за окно.
 * При нагрузке 1000 RPS с разных IP на разные книги в окне 30 мин —
 * ~1.8M записей (~200 байт каждая ≈ 360 MB). Для реального трафика
 * (books << 10K, IP-diversity умеренная) Map остаётся небольшой.
 *
 * <p>Паттерн идентичен {@code RateLimitFilter} — тот же {@link Clock}
 * inject (тесты fast-forward без sleep), то же lazy cleanup через счётчик.
 */
@Component
@EnableConfigurationProperties(BookViewDedupProperties.class)
public class BookViewDedupService {

    /** Раз в N вызовов запускаем lazy eviction expired entries. */
    private static final int CLEANUP_EVERY = 512;

    private final BookViewDedupProperties properties;
    private final Clock clock;

    /**
     * Key: {@code "<normalizedIp>:<bookId>"}. Value: момент последнего
     * засчитанного просмотра (instant записан ПОСЛЕ принятия решения
     * «increment»).
     */
    private final ConcurrentHashMap<String, Instant> lastSeen = new ConcurrentHashMap<>();

    private volatile int callCount = 0;

    public BookViewDedupService(BookViewDedupProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Принимает решение о засчитывании просмотра.
     *
     * <p>Если (ip, bookId) не встречался или окно уже истекло — возвращает
     * {@code true} и обновляет метку. Иначе — {@code false} (тихий no-op
     * для вызывающего).
     *
     * @param clientIp нормализованный IP клиента (после X-Forwarded-For)
     * @param bookId   UUID книги
     * @return {@code true} если инкремент должен быть применён
     */
    public boolean shouldIncrement(String clientIp, UUID bookId) {
        String key = clientIp + ":" + bookId;
        Instant now = clock.instant();
        Instant windowStart = now.minus(properties.dedupWindow());

        // computeIfPresent/compute не подходят для CAS-семантики с условием;
        // используем merge: если ключ отсутствует или значение устарело
        // (до windowStart) — записываем now и сигнализируем «increment».
        // Если значение свежее (>= windowStart) — оставляем его, сигнализируем
        // «no-op». Вся операция атомарна на уровне одного bucket в CHM.
        boolean[] increment = {false};
        lastSeen.compute(key, (k, existing) -> {
            if (existing == null || existing.isBefore(windowStart)) {
                // Первый раз или окно истекло — засчитываем
                increment[0] = true;
                return now;
            }
            // В пределах окна — дублирующий просмотр
            return existing;
        });

        maybeCleanup(now);
        return increment[0];
    }

    /**
     * Lazy eviction записей с истёкшим окном. Вызывается без
     * дополнительной синхронизации — {@link ConcurrentHashMap#entrySet()}
     * iterator безопасен для concurrent remove.
     */
    private void maybeCleanup(Instant now) {
        if (++callCount < CLEANUP_EVERY) {
            return;
        }
        callCount = 0;
        Instant windowStart = now.minus(properties.dedupWindow());
        Iterator<Map.Entry<String, Instant>> it = lastSeen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> e = it.next();
            if (e.getValue().isBefore(windowStart)) {
                it.remove();
            }
        }
    }

    /** Для тестов: принудительная очистка без ожидания threshold. */
    void cleanupNow() {
        callCount = CLEANUP_EVERY;
        maybeCleanup(clock.instant());
    }

    /** Для тестов: размер map (число активных (ip+book) пар в окне). */
    int stateSize() {
        return lastSeen.size();
    }

    /** Для тестов: сброс состояния между тестами в одном Spring context. */
    void resetState() {
        lastSeen.clear();
        callCount = 0;
    }
}
