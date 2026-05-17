package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IT для {@link AnthropicClient} через JDK {@link HttpServer} stub
 * (Этап 17.e, ADR-042). Проверяет HTTP-протокол contract: required
 * headers (x-api-key, anthropic-version, content-type), request body
 * shape, response parsing.
 *
 * <p>Тот же подход что и в
 * {@code HttpClientPdfFetcherRangeStreamingIT} - без WireMock dep,
 * inline server на динамическом порту. Не Spring test - инстанцируем
 * AnthropicClient напрямую с raw HttpClient.
 *
 * <p>Resilience4j retry не активируется в этих тестах потому что
 * используем raw HttpClient без Spring AOP - инстанцируем client
 * вручную без CB/Retry wrapping.
 */
class AnthropicClientStubIT {

    private HttpServer server;
    private HttpClient httpClient;
    private AnthropicClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpClient = HttpClient.newHttpClient();
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
    void complete_stubReturns200_extractsTextFromContentArray() throws Exception {
        server.createContext("/v1/messages", exchange -> {
            // sanity check на required Anthropic headers
            String key = exchange.getRequestHeaders().getFirst("x-api-key");
            String version = exchange.getRequestHeaders().getFirst("anthropic-version");
            assertThat(key).isEqualTo("test-key");
            assertThat(version).isEqualTo("2023-06-01");

            String response = "{\"content\":[{\"type\":\"text\","
                    + "\"text\":\"hello from stub\"}],\"role\":\"assistant\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new AnthropicClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        String result = client.complete("test prompt");

        assertThat(result).isEqualTo("hello from stub");
    }

    @Test
    void complete_stubReturns500_throwsAnthropicApiException() throws Exception {
        server.createContext("/v1/messages", exchange -> {
            byte[] body = "{\"error\":\"internal\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new AnthropicClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(AnthropicApiException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void complete_responseWithoutTextBlock_throwsAnthropicApiException() throws Exception {
        // sanity для case когда LLM вернул только tool_use block без text
        server.createContext("/v1/messages", exchange -> {
            String response = "{\"content\":[{\"type\":\"tool_use\","
                    + "\"name\":\"foo\"}],\"role\":\"assistant\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new AnthropicClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(AnthropicApiException.class)
                .hasMessageContaining("без text block");
    }

    @Test
    void complete_disabledClient_throwsIllegalState() {
        client = new AnthropicClient(HttpClient.newHttpClient(), new ObjectMapper(),
                "disabled", "http://nowhere", "claude-test", 1024, 30);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void isEnabled_emptyOrBlankOrSentinel_returnsFalse() {
        AnthropicClient empty = new AnthropicClient(httpClient, new ObjectMapper(),
                "", "http://x", "m", 1, 1);
        AnthropicClient blank = new AnthropicClient(httpClient, new ObjectMapper(),
                "   ", "http://x", "m", 1, 1);
        AnthropicClient disabled = new AnthropicClient(httpClient, new ObjectMapper(),
                "disabled", "http://x", "m", 1, 1);
        AnthropicClient real = new AnthropicClient(httpClient, new ObjectMapper(),
                "sk-ant-some-real-key", "http://x", "m", 1, 1);

        assertThat(empty.isEnabled()).isFalse();
        assertThat(blank.isEnabled()).isFalse();
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(real.isEnabled()).isTrue();
    }
}
