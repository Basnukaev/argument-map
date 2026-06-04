package ru.basnukaev.argumentmap.hadith.alminasa.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit-тест retry-политики alminasa (зеркало
 * {@code LlmTransientFailurePredicateTest}). Источник истины для
 * {@code resilience4j.retry.instances.alminasaApi.retry-exception-predicate}.
 * Тестируем предикат напрямую (детерминированно), не через AOP-обёртку.
 *
 * <p>Transient (retry): statusCode 0 (I/O), 429, 5xx. Permanent (НЕ retry):
 * 4xx, interrupt (-1), любое не-{@link AlminasaApiException}.
 */
class AlminasaTransientFailurePredicateTest {

    private final AlminasaTransientFailurePredicate predicate =
            new AlminasaTransientFailurePredicate();

    @Test
    void retries_transient_ioServerErrorRateLimit() {
        assertThat(predicate.test(new AlminasaApiException(0, "I/O"))).isTrue();
        assertThat(predicate.test(new AlminasaApiException(429, "rate limit"))).isTrue();
        assertThat(predicate.test(new AlminasaApiException(500, "server"))).isTrue();
        assertThat(predicate.test(new AlminasaApiException(503, "unavailable"))).isTrue();
    }

    @Test
    void doesNotRetry_permanent4xxAndInterrupt() {
        assertThat(predicate.test(new AlminasaApiException(400, "bad request"))).isFalse();
        assertThat(predicate.test(new AlminasaApiException(401, "unauthorized"))).isFalse();
        assertThat(predicate.test(new AlminasaApiException(404, "not found"))).isFalse();
        // -1 = прерывание потока, повтор бессмыслен
        assertThat(predicate.test(new AlminasaApiException(-1, "interrupted"))).isFalse();
    }

    @Test
    void doesNotRetry_unrelatedException() {
        assertThat(predicate.test(new RuntimeException("x"))).isFalse();
    }
}
