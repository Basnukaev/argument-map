package ru.basnukaev.argumentmap.library.pdf.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import ru.basnukaev.argumentmap.library.pdf.domain.PdfStreamingResult;
import ru.basnukaev.argumentmap.library.pdf.domain.RangeSpec;
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
 *
 * <p>{@link #openStream} (25.d.5, ADR-023 amendment) - lazy streaming
 * Range request к upstream. HTTP {@code Range} header добавляется если
 * {@link RangeSpec} != null. Возвращает {@link PdfStreamingResult} с
 * открытым InputStream напрямую от {@code HttpClient}, без temp file -
 * caller стримит bytes к клиенту по мере получения от upstream.
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

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "openStreamFallback")
    public PdfStreamingResult openStream(URI url, RangeSpec range) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(url)
                .timeout(TIMEOUT)
                .GET();
        if (range != null) {
            String headerValue = range.endInclusive() != null
                    ? "bytes=" + range.startInclusive() + "-" + range.endInclusive()
                    : "bytes=" + range.startInclusive() + "-";
            builder.header("Range", headerValue);
        }
        HttpRequest req = builder.build();

        try {
            HttpResponse<InputStream> resp = httpClient.send(
                    req, HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status != 200 && status != 206) {
                try {
                    resp.body().close();
                } catch (IOException ignored) {
                    // body уже невалиден - игнорируем
                }
                throw new ShamelaApiException(
                        "PDF stream вернул HTTP " + status + " на " + url
                                + " (Range: " + (range != null ? "bytes=" + range.startInclusive() + "-"
                                + (range.endInclusive() != null ? range.endInclusive() : "") : "none") + ")");
            }

            boolean isPartial = status == 206;
            long totalSize = parseTotalSizeFromContentRange(resp)
                    .orElseGet(() -> resp.headers().firstValueAsLong("Content-Length").orElse(-1L));
            long startInclusive = range != null ? range.startInclusive() : 0L;
            long headerContentLength = resp.headers()
                    .firstValueAsLong("Content-Length").orElse(-1L);
            // Некоторые CDN отдают 206 БЕЗ Content-Length. Прежняя формула
            // тогда давала отрицательный contentLength/-2 endInclusive (см.
            // deriveContentLength / deriveEndInclusive). Деривация
            // вынесена в чистые helper'ы с защитой от negative.
            long contentLength = deriveContentLength(
                    isPartial, headerContentLength,
                    parseEndFromContentRange(resp), startInclusive);
            long endInclusive = deriveEndInclusive(
                    isPartial, contentLength, startInclusive, totalSize);

            if (!isPartial && range != null) {
                log.warn("upstream {} проигнорировал Range header (вернул 200 вместо 206) - "
                        + "клиенту отдадим full content. Lazy streaming не работает", url);
            }

            return new PdfStreamingResult(
                    resp.body(), contentLength, startInclusive, endInclusive, totalSize, isPartial);
        } catch (IOException e) {
            throw new ShamelaApiException(
                    "ошибка открытия PDF stream: " + url + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShamelaApiException("прерван PDF stream open: " + url, e);
        }
    }

    /**
     * Парсит {@code Content-Range: bytes 0-1023/2048} и возвращает total
     * (последнее число после {@code /}). Возвращает empty если header
     * отсутствует или формат не совпадает.
     */
    private static OptionalLong parseTotalSizeFromContentRange(HttpResponse<?> resp) {
        Optional<String> header = resp.headers().firstValue("Content-Range");
        if (header.isEmpty()) {
            return OptionalLong.empty();
        }
        String value = header.get();
        int slash = value.lastIndexOf('/');
        if (slash < 0) {
            return OptionalLong.empty();
        }
        String totalStr = value.substring(slash + 1).trim();
        if (totalStr.equals("*")) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(totalStr));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Парсит {@code end} из {@code Content-Range: bytes <start>-<end>/<total>}
     * (число между {@code -} и {@code /}). Используется как резервный
     * источник длины когда 206-ответ пришёл без {@code Content-Length}.
     * Возвращает empty если header отсутствует / формат не совпадает /
     * это unsatisfied-range form (звёздочка вместо диапазона).
     */
    private static OptionalLong parseEndFromContentRange(HttpResponse<?> resp) {
        Optional<String> header = resp.headers().firstValue("Content-Range");
        if (header.isEmpty()) {
            return OptionalLong.empty();
        }
        String value = header.get();
        int slash = value.lastIndexOf('/');
        int dash = value.lastIndexOf('-');
        if (slash < 0 || dash < 0 || dash >= slash) {
            return OptionalLong.empty();
        }
        String endStr = value.substring(dash + 1, slash).trim();
        try {
            return OptionalLong.of(Long.parseLong(endStr));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Вычисляет длину тела ответа (для {@code Content-Length}). Приоритет:
     * <ol>
     *   <li>заголовок {@code Content-Length} если он валиден ({@code >= 0});</li>
     *   <li>иначе для 206 - длина из {@code Content-Range} ({@code end -
     *       start + 1}) если та неотрицательна;</li>
     *   <li>иначе unknown ({@code -1L}) - длину не выставляем.</li>
     * </ol>
     *
     * <p>Защита от бага: 206 без {@code Content-Length} раньше давал
     * {@code -1}, что ниже по коду вырождалось в отрицательный
     * {@code endInclusive}. Теперь никогда не возвращаем отрицательное
     * кроме явного sentinel-unknown {@code -1L}.
     */
    static long deriveContentLength(boolean isPartial, long headerContentLength,
                                    OptionalLong contentRangeEnd, long startInclusive) {
        if (headerContentLength >= 0) {
            return headerContentLength;
        }
        if (isPartial && contentRangeEnd.isPresent()) {
            long derived = contentRangeEnd.getAsLong() - startInclusive + 1;
            if (derived >= 0) {
                return derived;
            }
        }
        // длина неизвестна - sentinel, Content-Length не выставляем
        return -1L;
    }

    /**
     * Вычисляет последний байт диапазона (для {@code Content-Range}).
     * Для 206 c известной длиной - {@code start + length - 1}; иначе из
     * {@code totalSize} ({@code total - 1}). Если ни то ни другое не
     * известно - возвращает {@code -1L} (unknown), НИКОГДА отрицательное
     * как побочный эффект арифметики (раньше {@code contentLength - 1}
     * давал {@code -2}).
     */
    static long deriveEndInclusive(boolean isPartial, long contentLength,
                                   long startInclusive, long totalSize) {
        if (isPartial && contentLength > 0) {
            return startInclusive + contentLength - 1;
        }
        if (totalSize > 0) {
            return totalSize - 1;
        }
        return -1L;
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

    @SuppressWarnings("unused")  // вызывается через AOP при CB open
    private PdfStreamingResult openStreamFallback(URI url, RangeSpec range, Throwable cause) {
        log.warn("Circuit breaker pdfDownload открыт - fail fast для stream {} range={}: {}",
                url, range, cause.getMessage());
        throw new ShamelaApiException(
                "PDF stream временно недоступен (circuit breaker pdfDownload). "
                        + "Повтори через ~30 секунд. Причина: " + cause.getMessage(),
                cause);
    }
}
