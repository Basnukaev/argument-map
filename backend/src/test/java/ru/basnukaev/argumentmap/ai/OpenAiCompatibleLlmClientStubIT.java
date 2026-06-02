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
 * IT для {@link OpenAiCompatibleLlmClient} через JDK {@link HttpServer}
 * stub (ADR-058). Проверяет HTTP contract Chat Completions API:
 * Authorization Bearer header, endpoint /v1/chat/completions, messages
 * (system+user), парсинг choices[0].message.content.
 *
 * <p>Покрывает заодно {@link DeepSeekLlmClient} - тот же wire-формат,
 * subclass лишь меняет конфиг.
 */
class OpenAiCompatibleLlmClientStubIT {

    private HttpServer server;
    private HttpClient httpClient;
    private OpenAiCompatibleLlmClient client;

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
    void complete_withSystemAndUser_postsChatCompletionsWithBearer() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));

            String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"hi from openai stub\"}}]}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "sk-test", baseUrl, "gpt-test", 1024, 30);

        String result = client.complete("Ты ассистент.", "вопрос");

        assertThat(result).isEqualTo("hi from openai stub");
        assertThat(capturedAuth.get()).isEqualTo("Bearer sk-test");

        JsonNode sent = new ObjectMapper().readTree(capturedBody.get());
        JsonNode messages = sent.path("messages");
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("Ты ассистент.");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("вопрос");
        assertThat(sent.path("model").asText()).isEqualTo("gpt-test");
    }

    @Test
    void complete_withoutSystem_onlyUserMessage() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            String response = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "sk-test", baseUrl, "gpt-test", 1024, 30);

        client.complete("user only");

        JsonNode sent = new ObjectMapper().readTree(capturedBody.get());
        JsonNode messages = sent.path("messages");
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
    }

    @Test
    void complete_stubReturns500_throwsLlmApiException() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "sk-test", baseUrl, "gpt-test", 1024, 30);

        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void complete_responseWithoutChoices_throwsLlmApiException() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            String response = "{\"choices\":[]}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "sk-test", baseUrl, "gpt-test", 1024, 30);

        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("без choices");
    }

    @Test
    void complete_disabledClient_throwsIllegalState() {
        client = new OpenAiCompatibleLlmClient(HttpClient.newHttpClient(),
                new ObjectMapper(), "disabled", "http://nowhere", "m", 1, 1);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void isEnabled_sentinelOrBlank_returnsFalse() {
        assertThat(new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "disabled", "http://x", "m", 1, 1).isEnabled()).isFalse();
        assertThat(new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "", "http://x", "m", 1, 1).isEnabled()).isFalse();
        assertThat(new OpenAiCompatibleLlmClient(httpClient, new ObjectMapper(),
                "sk-real", "http://x", "m", 1, 1).isEnabled()).isTrue();
    }
}
