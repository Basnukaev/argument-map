package ru.basnukaev.argumentmap.library.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.library.pdf.domain.PdfStreamingResult;
import ru.basnukaev.argumentmap.library.pdf.domain.RangeSpec;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;

/**
 * IT для {@link HttpClientPdfFetcher#openStream(URI, RangeSpec)} - lazy
 * Range forwarding к upstream (25.d.5, ADR-023 amendment).
 *
 * <p>Поднимает локальный {@link HttpServer} на динамическом порту -
 * эмулируем archive.org с разными сценариями: Range supported (206),
 * Range ignored (200 OK на любой запрос), Range not satisfiable (416),
 * Server error (5xx).
 *
 * <p>JDK HttpServer выбран вместо WireMock - нет нового runtime
 * dependency, достаточно для unit-уровня контракта. Резолвит сценарии
 * быстрее WireMock (~10мс vs 50мс) - тесты gated на не-CB конфигурацию
 * не нужны.
 *
 * <p>Circuit breaker не активируется в тестах потому что используем
 * raw {@link HttpClient} без Spring AOP - инстанцируем fetcher вручную
 * без CB wrapping. Circuit breaker уже покрыт отдельно в
 * {@link HttpClientPdfFetcherCircuitBreakerIT}.
 */
class HttpClientPdfFetcherRangeStreamingIT {

    private HttpServer server;
    private HttpClient httpClient;
    private HttpClientPdfFetcher fetcher;
    private byte[] payload;

    @BeforeEach
    void setUp() throws IOException {
        payload = new byte[10_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpClient = HttpClient.newHttpClient();
        // raw fetcher без Spring proxy - circuit breaker не вмешивается
        fetcher = new HttpClientPdfFetcher(httpClient);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (httpClient != null) {
            // Java 21+ имеет httpClient.close(), но младше может не быть
            try {
                httpClient.close();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
    }

    @Test
    void openStream_upstreamRespondsWithFullContent_returnsNonPartialStream() throws Exception {
        server.createContext("/file.pdf", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file.pdf");

        try (PdfStreamingResult result = fetcher.openStream(url, null)) {
            assertThat(result.isPartial()).isFalse();
            assertThat(result.contentLength()).isEqualTo(payload.length);
            assertThat(result.stream().readAllBytes()).containsExactly(payload);
        }
    }

    @Test
    void openStream_upstreamReturns206WithRange_returnsPartialStream() throws Exception {
        server.createContext("/file.pdf", exchange -> {
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            assertThat(rangeHeader).isEqualTo("bytes=100-199");

            byte[] slice = new byte[100];
            System.arraycopy(payload, 100, slice, 0, slice.length);
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.getResponseHeaders().add("Content-Range",
                    "bytes 100-199/" + payload.length);
            exchange.sendResponseHeaders(206, slice.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(slice);
            }
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file.pdf");

        try (PdfStreamingResult result = fetcher.openStream(url, new RangeSpec(100L, 199L))) {
            assertThat(result.isPartial()).isTrue();
            assertThat(result.contentLength()).isEqualTo(100);
            assertThat(result.startInclusive()).isEqualTo(100);
            assertThat(result.endInclusive()).isEqualTo(199);
            assertThat(result.totalSize()).isEqualTo(payload.length);
            byte[] read = result.stream().readAllBytes();
            assertThat(read).hasSize(100);
            for (int i = 0; i < 100; i++) {
                assertThat(read[i]).isEqualTo(payload[100 + i]);
            }
        }
    }

    @Test
    void openStream_upstreamIgnoresRange_returns200_resultIsNotPartial() throws Exception {
        // archive.org mirror'ы могут проигнорировать Range и вернуть 200 OK с
        // полным content - наш fetcher должен это обработать без падения,
        // отдать stream с isPartial=false (controller отдаст 200 клиенту)
        server.createContext("/file.pdf", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file.pdf");

        try (PdfStreamingResult result = fetcher.openStream(url, new RangeSpec(100L, 199L))) {
            assertThat(result.isPartial()).isFalse();
            assertThat(result.contentLength()).isEqualTo(payload.length);
            assertThat(result.stream().readAllBytes()).containsExactly(payload);
        }
    }

    @Test
    void openStream_upstreamReturns5xx_throwsShamelaApiException() throws Exception {
        server.createContext("/file.pdf", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file.pdf");

        assertThatThrownBy(() -> fetcher.openStream(url, new RangeSpec(0L, 1023L)))
                .isInstanceOf(ShamelaApiException.class)
                .hasMessageContaining("503");
    }

    @Test
    void openStream_openEndedRange_addsCorrectRangeHeader() throws Exception {
        server.createContext("/file.pdf", exchange -> {
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            assertThat(rangeHeader).isEqualTo("bytes=5000-");

            int sliceLen = payload.length - 5000;
            byte[] slice = new byte[sliceLen];
            System.arraycopy(payload, 5000, slice, 0, sliceLen);
            exchange.getResponseHeaders().add("Content-Range",
                    "bytes 5000-" + (payload.length - 1) + "/" + payload.length);
            exchange.sendResponseHeaders(206, sliceLen);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(slice);
            }
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file.pdf");

        try (PdfStreamingResult result = fetcher.openStream(url, new RangeSpec(5000L, null))) {
            assertThat(result.isPartial()).isTrue();
            assertThat(result.startInclusive()).isEqualTo(5000);
            assertThat(result.endInclusive()).isEqualTo(payload.length - 1);
            assertThat(result.totalSize()).isEqualTo(payload.length);
        }
    }

    @Test
    void openStream_upstreamReturns416_throwsShamelaApiException() throws Exception {
        // archive.org может вернуть 416 если запросили range за пределами
        // файла. Наш fetcher оборачивает это в ShamelaApiException -
        // PdfLinksSourceProvider пробросит наружу, controller обработает
        // как 500 (это редкий path - normally controller сам валидирует
        // range против известного size при cache hit)
        server.createContext("/file.pdf", exchange -> {
            exchange.sendResponseHeaders(416, -1);
            exchange.close();
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file.pdf");

        assertThatThrownBy(() -> fetcher.openStream(url, new RangeSpec(1_000_000L, 2_000_000L)))
                .isInstanceOf(ShamelaApiException.class)
                .hasMessageContaining("416");
    }
}
