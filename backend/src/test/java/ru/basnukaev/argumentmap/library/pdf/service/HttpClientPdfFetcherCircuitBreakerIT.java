package ru.basnukaev.argumentmap.library.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;

/**
 * Проверяет интеграцию Resilience4j Circuit Breaker `pdfDownload` с
 * HttpClientPdfFetcher (Этап 25.b operational hardening).
 *
 * <p>Тестовые сценарии:
 * <ul>
 *   <li>CB зарегистрирован в реестре с правильными порогами</li>
 *   <li>Последовательные failures поднимают failure rate</li>
 *   <li>Достижение порога переключает CB в OPEN state</li>
 *   <li>В OPEN state - fallback method без upstream HTTP вызова</li>
 * </ul>
 *
 * <p>HttpClient mock'ается через MockBean чтобы fetch выбрасывал
 * controllable exceptions - не нужен реальный archive.org.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HttpClientPdfFetcherCircuitBreakerIT {

    @Autowired private HttpClientPdfFetcher fetcher;
    @Autowired private CircuitBreakerRegistry registry;

    @MockBean(name = "shamelaHttpClient")
    private java.net.http.HttpClient httpClient;

    @BeforeEach
    void resetCircuitBreaker() {
        // CB state переживает между тестами в одном SpringContext - reset
        // через transitionTo для чистого start каждого теста
        registry.circuitBreaker("pdfDownload").reset();
    }

    @Test
    void circuitBreaker_зарегистрирован_с_правильными_порогами_из_application_yml() {
        CircuitBreaker cb = registry.circuitBreaker("pdfDownload");

        assertThat(cb).isNotNull();
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void fetch_успешные_вызовы_оставляют_CB_в_CLOSED_state() throws Exception {
        // doReturn обходит generic type inference Mockito (HttpResponse<Path>)
        org.mockito.Mockito.doReturn(successfulResponse())
                .when(httpClient).send(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        URI url = URI.create("http://example.test/file.pdf");
        Path target = Paths.get(System.getProperty("java.io.tmpdir"), "cb-test-success.pdf");
        for (int i = 0; i < 6; i++) {
            try {
                fetcher.fetch(url, target);
            } catch (Exception ignored) {
                // не важно для этого теста, проверяем только state CB
            }
        }

        CircuitBreaker cb = registry.circuitBreaker("pdfDownload");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void fetch_5_подряд_failures_переключают_CB_в_OPEN() throws Exception {
        org.mockito.Mockito.doThrow(new java.io.IOException("simulated archive.org 503"))
                .when(httpClient).send(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        URI url = URI.create("http://example.test/file.pdf");
        Path target = Paths.get(System.getProperty("java.io.tmpdir"), "cb-test-fail.pdf");

        // 5 calls - заполняем minimumNumberOfCalls. failureRateThreshold=50%
        // достигнут (5/5 = 100%), CB переходит в OPEN.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> fetcher.fetch(url, target))
                    .isInstanceOf(ShamelaApiException.class);
        }

        CircuitBreaker cb = registry.circuitBreaker("pdfDownload");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void fetch_когда_CB_OPEN_срабатывает_fallback_без_HTTP_вызова() throws Exception {
        // принудительно открываем CB
        registry.circuitBreaker("pdfDownload").transitionToOpenState();

        URI url = URI.create("http://example.test/file.pdf");
        Path target = Paths.get(System.getProperty("java.io.tmpdir"), "cb-test-open.pdf");

        assertThatThrownBy(() -> fetcher.fetch(url, target))
                .isInstanceOf(ShamelaApiException.class)
                .hasMessageContaining("circuit breaker pdfDownload");

        // HTTP client не должен быть вызван т.к. CB intercepts
        org.mockito.Mockito.verify(httpClient, org.mockito.Mockito.never())
                .send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private java.net.http.HttpResponse<Path> successfulResponse() {
        return new java.net.http.HttpResponse<>() {
            @Override public int statusCode() { return 200; }
            @Override public java.net.http.HttpRequest request() { return null; }
            @Override public java.util.Optional<java.net.http.HttpResponse<Path>> previousResponse() {
                return java.util.Optional.empty();
            }
            @Override public java.net.http.HttpHeaders headers() {
                return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
            }
            @Override public Path body() { return Paths.get("/dev/null"); }
            @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return java.util.Optional.empty();
            }
            @Override public URI uri() { return URI.create("http://example.test/"); }
            @Override public java.net.http.HttpClient.Version version() {
                return java.net.http.HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
