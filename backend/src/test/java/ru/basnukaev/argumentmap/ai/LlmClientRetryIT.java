package ru.basnukaev.argumentmap.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * Lock-in IT: доказывает что {@code @Retry(name="llmApi")} реально
 * срабатывает ЧЕРЕЗ Spring-прокси {@link LlmClient} (регрессия Сессии
 * 55 — AiEdit-путь дёргал одноаргументную default-перегрузку, которая
 * self-invoke на raw target обходила прокси, и retry молча не работал).
 *
 * <p>В отличие от {@link AnthropicLlmClientStubIT} (raw ctor, без Spring
 * AOP — retry там не активен), здесь автовайрится прокси-бин и вызов
 * идёт через advice-цепочку. Stub-сервер отвечает 503, 503, 200 и
 * считает запросы — после трёх обращений (2 повтора + успех) клиент
 * должен вернуть тело 200-ответа.
 *
 * <p>{@code @DynamicPropertySource} лениво поднимает stub HttpServer (на
 * этапе резолва property нужен порт) и подставляет его в
 * {@code ai.anthropic.base-url}; api-key — реальный sentinel-нелайк
 * (isEnabled()=true). wait-duration урезан до 10ms — без него тест ждал
 * бы exponential backoff 2s+4s.
 */
@SpringBootTest(properties = {
        "ai.provider=anthropic",
        "ai.anthropic.api-key=sk-ant-test-retry-key",
        "ai.anthropic.model=claude-test",
        "ai.anthropic.max-tokens=64",
        // Быстрый retry: дефолтные 2s/4s backoff растянули бы тест.
        "resilience4j.retry.instances.llmApi.wait-duration=10ms",
        "resilience4j.retry.instances.llmApi.enable-exponential-backoff=false"
})
@Import(TestcontainersConfiguration.class)
class LlmClientRetryIT {

    private static HttpServer server;
    private static final AtomicInteger requestCount = new AtomicInteger(0);

    @AfterAll
    static void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @DynamicPropertySource
    static void stubBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("ai.anthropic.base-url", () -> "http://127.0.0.1:" + ensureStub());
    }

    /**
     * Лениво поднять stub-сервер (503, 503, 200) и вернуть его порт.
     * Делается в supplier'е {@code @DynamicPropertySource}, потому что
     * порт нужен уже на этапе резолва property — до любых JUnit
     * lifecycle-хуков.
     */
    private static synchronized int ensureStub() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось создать stub HttpServer", e);
        }
        server.createContext("/v1/messages", exchange -> {
            int n = requestCount.incrementAndGet();
            // Первые две попытки — 503 (transient → retry); третья — 200.
            if (n < 3) {
                byte[] body = "{\"error\":\"overloaded\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                String response = "{\"content\":[{\"type\":\"text\","
                        + "\"text\":\"recovered after retry\"}],\"role\":\"assistant\"}";
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    @Autowired
    private LlmClient llmClient;

    @Test
    void complete_transient503ThenThrough_retriesViaProxyAndSucceeds() {
        // Прокси-бин: @Retry advice в цепочке. complete(null, prompt) —
        // тот же двухаргументный метод что зовёт AiEditService.
        String result = llmClient.complete(null, "x");

        assertThat(result).isEqualTo("recovered after retry");
        // 2 повтора + финальный успех = ровно 3 запроса. Если бы retry не
        // прошёл через прокси — был бы 1 запрос и LlmApiException.
        assertThat(requestCount.get()).isEqualTo(3);
    }
}
