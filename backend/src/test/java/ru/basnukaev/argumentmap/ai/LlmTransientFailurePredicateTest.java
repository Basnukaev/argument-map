package ru.basnukaev.argumentmap.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Unit-тест retry-политики LLM (ADR-058, миграция из
 * AnthropicTransientFailurePredicateTest). Проверяет что retry
 * срабатывает ТОЛЬКО на transient ошибки (5xx, 429, IO/timeout), но НЕ
 * на permanent 4xx (400/401/403/404) - повтор последних только множит
 * cost+latency без шанса на успех.
 *
 * <p>Predicate - источник истины для
 * {@code resilience4j.retry.instances.llmApi.retry-exception-predicate}.
 * Тестируем его напрямую (детерминированно), а не через AOP-обёрнутый
 * клиент.
 */
class LlmTransientFailurePredicateTest {

    private final LlmTransientFailurePredicate predicate =
            new LlmTransientFailurePredicate();

    @Test
    void retries_serverError5xx() {
        assertThat(predicate.test(new LlmApiException("server", 500))).isTrue();
        assertThat(predicate.test(new LlmApiException("unavailable", 503))).isTrue();
        assertThat(predicate.test(new LlmApiException("gateway", 502))).isTrue();
    }

    @Test
    void retries_rateLimit429() {
        assertThat(predicate.test(new LlmApiException("rate limit", 429))).isTrue();
    }

    @Test
    void retries_connectionOrTimeout_statusCodeZero() {
        // IO error / timeout оборачивается в LlmApiException с code=0
        assertThat(predicate.test(
                new LlmApiException("timeout", 0, new IOException("read timed out"))))
                .isTrue();
    }

    @Test
    void retries_rawIoException() {
        assertThat(predicate.test(new IOException("connection reset"))).isTrue();
    }

    @Test
    void doesNotRetry_permanent4xx() {
        // КЛЮЧЕВОЙ кейс бага: 400/401/403/404 - постоянные, не повторяем
        assertThat(predicate.test(new LlmApiException("bad request", 400))).isFalse();
        assertThat(predicate.test(new LlmApiException("invalid key", 401))).isFalse();
        assertThat(predicate.test(new LlmApiException("forbidden", 403))).isFalse();
        assertThat(predicate.test(new LlmApiException("not found", 404))).isFalse();
        assertThat(predicate.test(new LlmApiException("conflict", 409))).isFalse();
        assertThat(predicate.test(new LlmApiException("unprocessable", 422))).isFalse();
    }

    @Test
    void doesNotRetry_invalidLlmJson_mappedTo200() {
        // невалидный JSON-ответ от LLM маппится в statusCode 200 -
        // permanent, повтор того же запроса не поможет
        assertThat(predicate.test(new LlmApiException("bad json", 200))).isFalse();
    }

    @Test
    void doesNotRetry_unrelatedException() {
        assertThat(predicate.test(new IllegalStateException("disabled"))).isFalse();
        assertThat(predicate.test(new RuntimeException("boom"))).isFalse();
    }
}
