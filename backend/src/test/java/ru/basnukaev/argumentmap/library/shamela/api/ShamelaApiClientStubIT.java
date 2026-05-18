package ru.basnukaev.argumentmap.library.shamela.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * инстанцируем {@link ShamelaApiClient} напрямую с raw HttpClient
 * (нам не нужен ни прокси ни Authenticator для тестов).
 *
 * <p>Использую расширение {@code .properties} для свойства
 * {@code metadataHost} - подменяет {@code dev.shamela.ws} на
 * {@code localhost:PORT} stub-сервера. {@code https://} в URL
 * подменяется на {@code http://} перехватом в {@link OverridingApiClient}.
 */
class ShamelaApiClientStubIT {

    private HttpServer server;
    private HttpClient httpClient;
    private OverridingApiClient client;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        httpClient = HttpClient.newHttpClient();
        ShamelaApiProperties props = new ShamelaApiProperties(
                "test-api-key",
                "127.0.0.1:" + port,
                "files.test",
                "/tmp/shamela-test",
                30,
                10
        );
        client = new OverridingApiClient(httpClient, props, new ObjectMapper());
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
            } catch (Throwable ignored) {
                // best-effort cleanup
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

    /**
     * Тестовый wrapper над {@link ShamelaApiClient}, который переопределяет
     * URL-схему с https на http (для stub HttpServer'а). Production-код
     * фиксирует https в URI.create - переопределяем через override URL
     * builder. Только {@code fetchMasterMetadata} и {@code fetchBookMetadata}
     * пробрасываются - другие методы (downloadArchive/Pdf) не нужны для
     * этих тестов.
     */
    static class OverridingApiClient extends ShamelaApiClient {
        private final HttpClient http;
        private final ShamelaApiProperties props;
        private final ObjectMapper mapper;

        OverridingApiClient(HttpClient http, ShamelaApiProperties props, ObjectMapper mapper) {
            super(http, props, mapper);
            this.http = http;
            this.props = props;
            this.mapper = mapper;
        }

        @Override
        public Optional<MasterMetadata> fetchMasterMetadata(int currentVersion) {
            URI uri = URI.create(String.format(
                    "http://%s/api/v1/patches/master?api_key=%s&version=%d",
                    props.metadataHost(), props.apiKey(), currentVersion));
            return doGet(uri, MasterMetadata.class);
        }

        @Override
        public ru.basnukaev.argumentmap.library.shamela.api.dto.BookMetadata fetchBookMetadata(
                long bookId, int majorRelease, int minorRelease) {
            URI uri = URI.create(String.format(
                    "http://%s/api/v1/patches/book-updates/%d?api_key=%s&major_release=%d&minor_release=%d",
                    props.metadataHost(), bookId, props.apiKey(), majorRelease, minorRelease));
            return doGet(uri, ru.basnukaev.argumentmap.library.shamela.api.dto.BookMetadata.class)
                    .orElseThrow(() -> new ShamelaApiException(
                            "shamela book metadata вернула пустое тело для bookId=" + bookId));
        }

        // Та же логика что в private getJson() production-класса.
        // Вынесена отдельно потому что URI строится с http:// scheme.
        private <T> Optional<T> doGet(URI uri, Class<T> type) {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            try {
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() / 100 != 2) {
                    throw new ShamelaApiException("shamela API вернула HTTP " + resp.statusCode());
                }
                if (resp.body().length == 0) {
                    return Optional.empty();
                }
                return Optional.of(mapper.readValue(resp.body(), type));
            } catch (java.io.IOException e) {
                throw new ShamelaApiException("ошибка stub", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ShamelaApiException("прерван stub", e);
            }
        }
    }
}
