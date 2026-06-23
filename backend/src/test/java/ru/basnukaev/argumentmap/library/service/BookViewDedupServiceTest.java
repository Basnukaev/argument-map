package ru.basnukaev.argumentmap.library.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.library.config.BookViewDedupProperties;

/**
 * Unit-тест для {@link BookViewDedupService}.
 *
 * <p>Проверяет: повторный вызов с тем же (ip+bookId) в пределах окна
 * не засчитывается; смена ip или bookId — засчитывается; по истечении
 * окна — засчитывается снова. Без Spring context; mutable clock
 * fast-forward'ится без sleep, state сервиса сохраняется.
 */
class BookViewDedupServiceTest {

    private static final Duration WINDOW = Duration.ofMinutes(30);

    /**
     * Mutable clock — {@link #tick} меняет внутренний instant. Адаптер
     * позволяет fast-forward без пересоздания сервиса (state сохраняется).
     */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration delta) { now = now.plus(delta); }
    }

    private MutableClock clock;
    private BookViewDedupService dedup;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        dedup = new BookViewDedupService(new BookViewDedupProperties(WINDOW), clock);
    }

    /** Первый просмотр засчитывается всегда. */
    @Test
    void firstViewAlwaysIncrements() {
        assertThat(dedup.shouldIncrement("1.2.3.4", UUID.randomUUID())).isTrue();
    }

    /**
     * Повторный вызов с тем же IP и той же книгой в пределах окна —
     * no-op (дублирующий просмотр, анти-инфляция).
     */
    @Test
    void repeatWithinWindowIsNoOp() {
        UUID bookId = UUID.randomUUID();
        String ip = "1.2.3.4";

        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(dedup.shouldIncrement(ip, bookId)).isFalse();
    }

    /** После истечения окна просмотр с того же IP засчитывается снова. */
    @Test
    void afterWindowExpiresViewCountsAgain() {
        UUID bookId = UUID.randomUUID();
        String ip = "10.0.0.1";

        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();

        clock.advance(WINDOW.plusSeconds(1));
        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();
    }

    /** Разные IP на одну книгу — каждый засчитывается независимо. */
    @Test
    void differentIpsSameBookBothIncrement() {
        UUID bookId = UUID.randomUUID();

        assertThat(dedup.shouldIncrement("1.1.1.1", bookId)).isTrue();
        assertThat(dedup.shouldIncrement("2.2.2.2", bookId)).isTrue();
    }

    /** Один IP, разные книги — каждая книга засчитывается независимо. */
    @Test
    void sameIpDifferentBooksBothIncrement() {
        String ip = "5.5.5.5";

        assertThat(dedup.shouldIncrement(ip, UUID.randomUUID())).isTrue();
        assertThat(dedup.shouldIncrement(ip, UUID.randomUUID())).isTrue();
    }

    /** Ровно на границе окна (without +1 сек) — ещё считается дублем. */
    @Test
    void exactlyAtWindowBoundaryIsStillNoOp() {
        UUID bookId = UUID.randomUUID();
        String ip = "3.3.3.3";

        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();

        // windowStart = now - 30m; existing == windowStart → не isBefore → дубль
        clock.advance(WINDOW);
        assertThat(dedup.shouldIncrement(ip, bookId)).isFalse();
    }

    /** На 1 секунду после окна — новый просмотр. */
    @Test
    void oneSecondAfterWindowAllowsIncrement() {
        UUID bookId = UUID.randomUUID();
        String ip = "4.4.4.4";

        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();

        clock.advance(WINDOW.plusSeconds(1));
        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();
    }

    /** Многократные дубли в одном окне остаются no-op. */
    @Test
    void multipleRepeatsInWindowAllNoOp() {
        UUID bookId = UUID.randomUUID();
        String ip = "6.6.6.6";

        assertThat(dedup.shouldIncrement(ip, bookId)).isTrue();
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            assertThat(dedup.shouldIncrement(ip, bookId))
                    .as("итерация %d должна быть no-op", i)
                    .isFalse();
        }
    }

    /**
     * Cleanup evict'ирует истёкшие записи. После advance за окно и
     * принудительного cleanup — stateSize уменьшается.
     */
    @Test
    void cleanupEvictsExpiredEntries() {
        UUID bookId = UUID.randomUUID();
        dedup.shouldIncrement("7.7.7.7", bookId);
        assertThat(dedup.stateSize()).isEqualTo(1);

        clock.advance(WINDOW.plusSeconds(1));
        dedup.cleanupNow();
        assertThat(dedup.stateSize()).isEqualTo(0);
    }
}
