package ru.basnukaev.argumentmap.library.shamela.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.library.shamela.api.dto.MasterMetadata;

/**
 * IT для {@link ShamelaApiClient} через JDK {@link HttpServer} stub.
 * Фокус - семантика "2xx + пустое body = uptodate" (зафиксирована
 * в Сессии 39 после реального failure на dev.shamela.ws после первого
 * успешного sync). Без этого теста любой будущий рефакторинг
 * {@link ShamelaApiClient#fetchMasterMetadata(int)} мог бы тихо
 * сломать сценарий, который ловили в проде.
 *
 * <p>Тот же подход что и в {@code AnthropicClientStubIT} - inline
 * server на динамическом порту, без WireMock dep. Не Spring test:
 * инстанцируем production {@link ShamelaApiClient} напрямую с
 * properties где {@code metadataScheme=http} и {@code metadataHost}
 * указывает на stub-сервер. Тестируется боевой класс, не override.
 */
class ShamelaApiClientStubIT {

    private HttpServer server;
    private HttpClient httpClient;
    private ShamelaApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        httpClient = HttpClient.newHttpClient();
        ShamelaApiProperties props = new ShamelaApiProperties(
                "test-api-key",
                "127.0.0.1:" + port,
                "files.test",
                "/tmp/shamela-test",
                30,
                10,
                "http"
        );
        client = new ShamelaApiClient(httpClient, props, new ObjectMapper());
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
                // best-effort cleanup, HttpClient.close может бросить unchecked
            }
        }
    }

    @Test
    void fetchMasterMetadata_emptyBody_returnsEmptyOptional() {
        // shamela сигнализирует uptodate через 2xx + zero-length body
        server.createContext("/api/v1/patches/master", exchange -> {
            exchange.sendResponseHeaders(200, -1); // -1 = no body, Content-Length: 0
            exchange.close();
        });

        Optional<MasterMetadata> result = client.fetchMasterMetadata(1261);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchMasterMetadata_normalJsonResponse_returnsParsedMetadata() {
        String json = "{\"patch_url\":\"https://dev.shamela.ws/master-0-1261.zip\",\"version\":1261}";
        server.createContext("/api/v1/patches/master", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        Optional<MasterMetadata> result = client.fetchMasterMetadata(0);

        assertThat(result).isPresent();
        assertThat(result.get().version()).isEqualTo(1261);
        assertThat(result.get().patchUrl()).isEqualTo("https://dev.shamela.ws/master-0-1261.zip");
    }

    @Test
    void fetchMasterMetadata_204NoContent_returnsEmptyOptional() {
        // 204 No Content - альтернативный способ сигнализировать "ничего нового".
        // body.length == 0 одинаково для 200/204 - оба должны давать Optional.empty()
        server.createContext("/api/v1/patches/master", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        Optional<MasterMetadata> result = client.fetchMasterMetadata(1261);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchBookMetadata_emptyBody_throwsApiException() {
        // Для book metadata пустое body это аномалия - явный exception
        server.createContext("/api/v1/patches/book-updates/42", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client.fetchBookMetadata(42L, 0, 0))
                .isInstanceOf(ShamelaApiException.class)
                .hasMessageContaining("пустое тело")
                .hasMessageContaining("bookId=42");
    }

    @Test
    void fetchMasterMetadata_5xxError_throwsApiException() {
        // Регрессия - не-2xx по-прежнему бросает exception
        server.createContext("/api/v1/patches/master", exchange -> {
            byte[] body = "{\"error\":\"internal\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> client.fetchMasterMetadata(1261))
                .isInstanceOf(ShamelaApiException.class)
                .hasMessageContaining("HTTP 500");
    }
}
