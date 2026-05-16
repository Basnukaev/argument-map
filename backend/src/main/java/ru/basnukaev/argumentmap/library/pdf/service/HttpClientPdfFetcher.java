package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;

/**
 * Production реализация {@link PdfFetcher} через
 * {@code java.net.http.HttpClient}. Использует тот же
 * {@code shamelaHttpClient} bean что и shamela ETL - с corporate
 * proxy support из {@code ShamelaHttpClientConfig}.
 *
 * <p>Защищён Resilience4j Circuit Breaker {@code pdfDownload} (ADR-024,
 * Этап 25.b). archive.org может отвечать 503/timeout под нагрузкой -
 * без CB каскадная failure: пользователь жмёт «Открыть PDF», 5 минут
 * висит, сервер занят retries upstream. С CB - после 5 failures из 10
 * (50%) circuit opens, далее 30 секунд все запросы получают
 * {@link ShamelaApiException} мгновенно ({@code @CircuitBreaker}
 * fallback) → 503 наружу. После 30s - half-open, 3 пробных request'а,
 * при успехе close. Конфиг в {@code application.yml} resilience4j.
 */
@Component
public class HttpClientPdfFetcher implements PdfFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpClientPdfFetcher.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final String CB_NAME = "pdfDownload";

    private final HttpClient httpClient;

    public HttpClientPdfFetcher(@Qualifier("shamelaHttpClient") HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fetchFallback")
    public void fetch(URI url, Path target) {
        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Path> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() / 100 != 2) {
                Files.deleteIfExists(target);
                throw new ShamelaApiException(
                        "PDF download вернул HTTP " + resp.statusCode() + " на " + url);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // частично скачанный файл оставляем для диагностики
            }
            throw new ShamelaApiException(
                    "ошибка PDF download: " + url + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShamelaApiException("прерван PDF download: " + url, e);
        }
    }

    /**
     * Fallback при открытом circuit breaker'е. Сигнатура mirror главного
     * метода + дополнительный последний параметр {@link Throwable}
     * (контракт resilience4j-spring-boot3). Любой fallback method
     * должен иметь identical args order + Throwable.
     *
     * <p>Поведение - быстрый fail вместо blocking. Пользователь увидит
     * «PDF временно недоступен» в течение 30 секунд (waitDurationInOpenState),
     * затем half-open проба восстановит download если archive.org поднялся.
     */
    @SuppressWarnings("unused")  // вызывается через AOP при CB open
    private void fetchFallback(URI url, Path target, Throwable cause) {
        log.warn("Circuit breaker pdfDownload открыт - fail fast для {}: {}",
                url, cause.getMessage());
        throw new ShamelaApiException(
                "PDF download временно недоступен (circuit breaker pdfDownload). "
                        + "Повтори через ~30 секунд. Причина: " + cause.getMessage(),
                cause);
    }
}
