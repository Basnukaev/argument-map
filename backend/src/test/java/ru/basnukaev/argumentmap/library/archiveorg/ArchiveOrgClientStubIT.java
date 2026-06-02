package ru.basnukaev.argumentmap.library.archiveorg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IT для {@link ArchiveOrgClient} через JDK {@link HttpServer} stub
 * (ADR-056). Тот же приём что {@code ShamelaApiClientStubIT}: inline
 * server на динамическом порту, production-класс с {@code base-url}
 * указывающим на stub. Без circuit breaker (он через Spring AOP, тут
 * проверяем core-логику парсинга/HTTP).
 *
 * <p>{@link #extractIdentifier_variants()} - чистая логика без сети.
 */
class ArchiveOrgClientStubIT {

    private HttpServer server;
    private HttpClient httpClient;
    private ArchiveOrgClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        httpClient = HttpClient.newHttpClient();
        ArchiveOrgProperties props = new ArchiveOrgProperties("http://127.0.0.1:" + port, 30, 10);
        client = new ArchiveOrgClient(httpClient, props, new ObjectMapper());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    @Test
    void extractIdentifier_variants() {
        assertThat(client.extractIdentifier(
                "https://archive.org/details/fmhji/fmhji1/page/70/mode/2up")).isEqualTo("fmhji");
        assertThat(client.extractIdentifier("archive.org/details/fmhji")).isEqualTo("fmhji");
        assertThat(client.extractIdentifier("https://archive.org/details/fmhji")).isEqualTo("fmhji");
        assertThat(client.extractIdentifier("fmhji")).isEqualTo("fmhji");
        // identifier с дефисами (реальный кейс sahih-bukhari-arabic)
        assertThat(client.extractIdentifier("sahih-bukhari-arabic")).isEqualTo("sahih-bukhari-arabic");
        // query/fragment отрезаются
        assertThat(client.extractIdentifier("https://archive.org/details/foo?x=1#y")).isEqualTo("foo");
    }

    @Test
    void extractIdentifier_invalid_throws() {
        assertThatThrownBy(() -> client.extractIdentifier(""))
                .isInstanceOf(InvalidArchiveOrgUrlException.class);
        assertThatThrownBy(() -> client.extractIdentifier("https://example.com/book/123"))
                .isInstanceOf(InvalidArchiveOrgUrlException.class)
                .hasMessageContaining("details");
        assertThatThrownBy(() -> client.extractIdentifier("https://archive.org/details/"))
                .isInstanceOf(InvalidArchiveOrgUrlException.class);
    }

    @Test
    void fetchMetadata_parsesMetadataAndFiles() {
        String json = "{\"metadata\":{\"title\":\"كتاب\",\"creator\":\"المؤلف\",\"language\":\"Arabic\"},"
                + "\"files\":[{\"name\":\"x1.pdf\",\"format\":\"Image Container PDF\","
                + "\"source\":\"original\",\"size\":\"123\"}]}";
        server.createContext("/metadata/x", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        ArchiveOrgMetadata result = client.fetchMetadata("x");

        assertThat(result.metadata()).containsEntry("title", "كتاب");
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).name()).isEqualTo("x1.pdf");
        assertThat(result.files().get(0).sizeBytes()).isEqualTo(123L);
    }

    @Test
    void fetchMetadata_emptyObject_throwsItemNotFound() {
        // archive.org на несуществующий/удалённый item отдаёт 200 + пустой {}
        server.createContext("/metadata/missing", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> client.fetchMetadata("missing"))
                .isInstanceOf(ArchiveOrgItemNotFoundException.class);
    }

    @Test
    void fetchMetadata_5xx_throwsArchiveOrgException() {
        server.createContext("/metadata/boom", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client.fetchMetadata("boom"))
                .isInstanceOf(ArchiveOrgException.class)
                .hasMessageContaining("HTTP 503");
    }

    // ---------------- live (исключён из обычного verify) ----------------

    @Test
    @org.junit.jupiter.api.Tag("live")
    void liveFmhji_realArchiveOrgCall() {
        ArchiveOrgProperties liveProps = new ArchiveOrgProperties("https://archive.org", 30, 10);
        ArchiveOrgClient live = new ArchiveOrgClient(
                HttpClient.newHttpClient(), liveProps, new ObjectMapper());

        ArchiveOrgMetadata raw = live.fetchMetadata("fmhji");

        assertThat(raw.metadata()).containsKey("title");
        assertThat(raw.files()).isNotEmpty();
    }
}
