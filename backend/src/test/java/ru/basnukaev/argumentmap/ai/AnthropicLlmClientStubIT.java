package ru.basnukaev.argumentmap.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IT для {@link AnthropicLlmClient} через JDK {@link HttpServer} stub
 * (ADR-058, миграция из AnthropicClientStubIT). Проверяет HTTP-протокол
 * contract: required headers (x-api-key, anthropic-version), request
 * body shape (включая top-level system), response parsing.
 *
 * <p>Не Spring test - инстанцируем клиент напрямую с raw HttpClient.
 * Resilience4j retry не активируется (нет Spring AOP).
 */
class AnthropicLlmClientStubIT {

    private HttpServer server;
    private HttpClient httpClient;
    private AnthropicLlmClient client;

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
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/messages", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("x-api-key");
            String version = exchange.getRequestHeaders().getFirst("anthropic-version");
            assertThat(key).isEqualTo("test-key");
            assertThat(version).isEqualTo("2023-06-01");
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));

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
        client = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        String result = client.complete(null, "test prompt");

        assertThat(result).isEqualTo("hello from stub");
        // без systemPrompt - top-level system отсутствует в body
        JsonNode sent = new ObjectMapper().readTree(capturedBody.get());
        assertThat(sent.has("system")).isFalse();
        assertThat(sent.path("messages").get(0).path("content").asText())
                .isEqualTo("test prompt");
    }

    @Test
    void complete_withSystemPrompt_addsTopLevelSystemField() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/messages", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            String response = "{\"content\":[{\"type\":\"text\","
                    + "\"text\":\"ok\"}],\"role\":\"assistant\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        client.complete("Ты ассистент.", "user message");

        JsonNode sent = new ObjectMapper().readTree(capturedBody.get());
        assertThat(sent.path("system").asText()).isEqualTo("Ты ассистент.");
        assertThat(sent.path("messages").get(0).path("role").asText())
                .isEqualTo("user");
        assertThat(sent.path("messages").get(0).path("content").asText())
                .isEqualTo("user message");
    }

    @Test
    void complete_blankSystemPrompt_omitsSystemField() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/messages", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            String response = "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        client.complete("   ", "user message");

        JsonNode sent = new ObjectMapper().readTree(capturedBody.get());
        assertThat(sent.has("system")).isFalse();
    }

    @Test
    void complete_stubReturns500_throwsLlmApiException() throws Exception {
        server.createContext("/v1/messages", exchange -> {
            byte[] body = "{\"error\":\"internal\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        assertThatThrownBy(() -> client.complete(null, "prompt"))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void complete_responseWithoutTextBlock_throwsLlmApiException() throws Exception {
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
        client = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "test-key", baseUrl, "claude-test", 1024, 30);

        assertThatThrownBy(() -> client.complete(null, "prompt"))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("без text block");
    }

    @Test
    void complete_disabledClient_throwsIllegalState() {
        client = new AnthropicLlmClient(HttpClient.newHttpClient(), new ObjectMapper(),
                "disabled", "http://nowhere", "claude-test", 1024, 30);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.complete(null, "prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void isEnabled_emptyOrBlankOrSentinel_returnsFalse() {
        AnthropicLlmClient empty = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "", "http://x", "m", 1, 1);
        AnthropicLlmClient blank = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "   ", "http://x", "m", 1, 1);
        AnthropicLlmClient disabled = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "disabled", "http://x", "m", 1, 1);
        AnthropicLlmClient real = new AnthropicLlmClient(httpClient, new ObjectMapper(),
                "sk-ant-some-real-key", "http://x", "m", 1, 1);

        assertThat(empty.isEnabled()).isFalse();
        assertThat(blank.isEnabled()).isFalse();
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(real.isEnabled()).isTrue();
    }
}
