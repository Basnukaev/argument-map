package ru.basnukaev.argumentmap.hadith.alminasa.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;

/**
 * Lock-in IT: {@code @Retry(name="alminasaApi")} срабатывает через
 * Spring-прокси {@link AlminasaEsClient} (ср. {@code LlmClientRetryIT} —
 * регрессия Сессии 55 с self-invocation). Stub: 503, 503, 200.
 */
@SpringBootTest(properties = {
        // Быстрый retry — без override тест ждал бы exponential backoff 2s+4s.
        "resilience4j.retry.instances.alminasaApi.wait-duration=10ms",
        "resilience4j.retry.instances.alminasaApi.enable-exponential-backoff=false"
})
@Import(TestcontainersConfiguration.class)
class AlminasaEsClientRetryIT {

    private static HttpServer server;
    // Последовательность 503,503,200 рассчитана ровно на один @Test; при
    // добавлении второго теста — сбрасывать счётчик и переписывать stub на
    // per-test очередь ответов.
    private static final AtomicInteger requestCount = new AtomicInteger(0);

    @Autowired
    private AlminasaEsClient client;

    @AfterAll
    static void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @DynamicPropertySource
    static void stubBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("alminasa.base-url", () -> "http://127.0.0.1:" + ensureStub());
    }

    private static synchronized int ensureStub() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось создать stub HttpServer", e);
        }
        server.createContext("/", exchange -> {
            int n = requestCount.incrementAndGet();
            byte[] body;
            int status;
            if (n < 3) {
                status = 503;
                body = "{\"error\":\"overloaded\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                status = 200;
                try (InputStream in = AlminasaEsClientRetryIT.class
                        .getResourceAsStream("/alminasa/hadith-page-empty.json")) {
                    body = in.readAllBytes();
                }
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    @Test
    void retry_503_503_200_успешен_после_трёх_запросов() {
        AlminasaPage page = client.fetchHadithPage(null, null, 10);

        assertThat(requestCount.get()).isEqualTo(3);
        assertThat(page.hits()).isEmpty();
        assertThat(page.totalHits()).isEqualTo(21); // total из фикстуры
    }
}
