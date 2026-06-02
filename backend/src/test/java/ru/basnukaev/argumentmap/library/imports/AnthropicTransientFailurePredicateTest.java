package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Unit-тест retry-политики Anthropic (Bug-hunt Tier-3 #3). Проверяет что
 * retry срабатывает ТОЛЬКО на transient ошибки (5xx, 429, IO/timeout), но
 * НЕ на permanent 4xx (400/401/403/404) - повтор последних только множит
 * cost+latency без шанса на успех.
 *
 * <p>Predicate - источник истины для
 * {@code resilience4j.retry.instances.anthropicApi.retry-exception-predicate}.
 * Тестируем его напрямую (детерминированно), а не через AOP-обёрнутый
 * клиент: AOP-retry не активен в standalone-инстансе клиента
 * (AnthropicClientStubIT тоже инстанцирует raw, без Spring proxy).
 */
class AnthropicTransientFailurePredicateTest {

    private final AnthropicTransientFailurePredicate predicate =
            new AnthropicTransientFailurePredicate();

    @Test
    void retries_serverError5xx() {
        assertThat(predicate.test(new AnthropicApiException("server", 500))).isTrue();
        assertThat(predicate.test(new AnthropicApiException("unavailable", 503))).isTrue();
        assertThat(predicate.test(new AnthropicApiException("gateway", 502))).isTrue();
    }

    @Test
    void retries_rateLimit429() {
        assertThat(predicate.test(new AnthropicApiException("rate limit", 429))).isTrue();
    }

    @Test
    void retries_connectionOrTimeout_statusCodeZero() {
        // IO error / timeout оборачивается в AnthropicApiException с code=0
        assertThat(predicate.test(
                new AnthropicApiException("timeout", 0, new IOException("read timed out"))))
                .isTrue();
    }

    @Test
    void retries_rawIoException() {
        assertThat(predicate.test(new IOException("connection reset"))).isTrue();
    }

    @Test
    void doesNotRetry_permanent4xx() {
        // КЛЮЧЕВОЙ кейс бага: 400/401/403/404 - постоянные, не повторяем
        assertThat(predicate.test(new AnthropicApiException("bad request", 400))).isFalse();
        assertThat(predicate.test(new AnthropicApiException("invalid key", 401))).isFalse();
        assertThat(predicate.test(new AnthropicApiException("forbidden", 403))).isFalse();
        assertThat(predicate.test(new AnthropicApiException("not found", 404))).isFalse();
        assertThat(predicate.test(new AnthropicApiException("conflict", 409))).isFalse();
        assertThat(predicate.test(new AnthropicApiException("unprocessable", 422))).isFalse();
    }

    @Test
    void doesNotRetry_invalidLlmJson_mappedTo200() {
        // невалидный JSON-ответ от LLM маппится в statusCode 200 (см.
        // AiEditService.validateProseMirrorJson / AnthropicClient.extractText) -
        // permanent, повтор того же запроса не поможет
        assertThat(predicate.test(new AnthropicApiException("bad json", 200))).isFalse();
    }

    @Test
    void doesNotRetry_unrelatedException() {
        assertThat(predicate.test(new IllegalStateException("disabled"))).isFalse();
        assertThat(predicate.test(new RuntimeException("boom"))).isFalse();
    }
}
