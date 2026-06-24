package ru.basnukaev.argumentmap.hadith.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.config.MatnTranslateRateLimitProperties;
import ru.basnukaev.argumentmap.hadith.web.MatnTranslateRateLimitExceededException;

/**
 * Unit-тест для {@link MatnTranslateRateLimiter} (cost-guard P2-3).
 *
 * <p>Проверяет: до лимита acquire не бросает; на (limit+1)-м — 429 с
 * корректным limit/retryAfter; per-user изоляция (один юзер не влияет на
 * другого); сброс окна по истечении window; disabled = no-op; cleanup
 * evict-ит idle. Mutable clock fast-forward'ится без sleep — паттерн
 * {@code BookViewDedupServiceTest}.
 */
class MatnTranslateRateLimiterTest {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration delta) { now = now.plus(delta); }
    }

    private MutableClock clock;
    private MatnTranslateRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new MatnTranslateRateLimiter(
                new MatnTranslateRateLimitProperties(true, LIMIT, WINDOW), clock);
    }

    @Test
    void underLimitDoesNotThrow() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            int call = i;
            assertThatNoException()
                    .as("вызов #%d из %d", call + 1, LIMIT)
                    .isThrownBy(() -> limiter.acquire(user));
        }
    }

    @Test
    void overLimitThrows429WithLimitAndRetryAfter() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(user);
        }
        assertThatExceptionOfType(MatnTranslateRateLimitExceededException.class)
                .isThrownBy(() -> limiter.acquire(user))
                .satisfies(ex -> {
                    assertThat(ex.getLimit()).isEqualTo(LIMIT);
                    // окно час, ни секунды не прошло — retryAfter ≈ 3600, минимум 1
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1L, WINDOW.toSeconds());
                });
    }

    @Test
    void rejectedCallDoesNotConsumeSlot() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(user);
        }
        // 4-й бросает, но НЕ записывается в окно — после окна снова ровно LIMIT слотов
        assertThatExceptionOfType(MatnTranslateRateLimitExceededException.class)
                .isThrownBy(() -> limiter.acquire(user));

        clock.advance(WINDOW.plusSeconds(1));
        for (int i = 0; i < LIMIT; i++) {
            assertThatNoException().isThrownBy(() -> limiter.acquire(user));
        }
    }

    @Test
    void perUserIsolation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(userA);
        }
        assertThatExceptionOfType(MatnTranslateRateLimitExceededException.class)
                .isThrownBy(() -> limiter.acquire(userA));
        // userB не затронут лимитом userA
        assertThatNoException().isThrownBy(() -> limiter.acquire(userB));
    }

    @Test
    void windowExpiryFreesBudget() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            limiter.acquire(user);
        }
        assertThatExceptionOfType(MatnTranslateRateLimitExceededException.class)
                .isThrownBy(() -> limiter.acquire(user));

        clock.advance(WINDOW.plusSeconds(1));
        assertThatNoException().isThrownBy(() -> limiter.acquire(user));
    }

    @Test
    void disabledIsNoOp() {
        MatnTranslateRateLimiter disabled = new MatnTranslateRateLimiter(
                new MatnTranslateRateLimitProperties(false, LIMIT, WINDOW), clock);
        UUID user = UUID.randomUUID();
        // далеко за лимит — никаких исключений
        for (int i = 0; i < LIMIT * 5; i++) {
            assertThatNoException().isThrownBy(() -> disabled.acquire(user));
        }
    }

    @Test
    void cleanupEvictsIdleUsers() {
        UUID user = UUID.randomUUID();
        limiter.acquire(user);
        assertThat(limiter.stateSize()).isEqualTo(1);

        // отмотать за IDLE_EVICT (1ч), затем cleanup
        clock.advance(Duration.ofHours(2));
        limiter.cleanupNow();
        assertThat(limiter.stateSize()).isZero();
    }
}
