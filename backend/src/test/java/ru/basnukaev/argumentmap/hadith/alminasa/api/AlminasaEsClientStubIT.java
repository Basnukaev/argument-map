package ru.basnukaev.argumentmap.hadith.alminasa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;

/**
 * Standalone stub-IT клиента alminasa: форма запросов (путь, заголовки
 * Origin/Referer, тело ES Query DSL) и парсинг ответов на реальных
 * HAR-фикстурах. Без Spring — @Retry здесь не активен (см.
 * {@link AlminasaEsClientRetryIT} для retry-поведения через прокси).
 */
class AlminasaEsClientStubIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private AlminasaEsClient client;
    private final AtomicReference<String> fixtureToServe = new AtomicReference<>();
    private final AtomicReference<Integer> statusToServe = new AtomicReference<>(200);
    private record CapturedRequest(String path, Map<String, List<String>> headers, String body) {}
    private final ConcurrentLinkedQueue<CapturedRequest> captured = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.add(new CapturedRequest(
                    exchange.getRequestURI().getPath(), exchange.getRequestHeaders(), body));
            byte[] resp = fixture(fixtureToServe.get());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusToServe.get(), resp.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(resp);
            }
        });
        server.start();
        AlminasaProperties props = new AlminasaProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "https://alminasa.ai",
                "es-prod-euw1-", "-read",
                null, 5, 5,
                new AlminasaProperties.Crawl(100, 0, 25, 500, 10));
        client = new AlminasaEsClient(HttpClient.newHttpClient(), props, MAPPER);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = AlminasaEsClientStubIT.class.getResourceAsStream("/alminasa/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void fetchHadithPage_первая_страница_без_search_after() throws IOException {
        fixtureToServe.set("hadith-page.json");

        AlminasaPage page = client.fetchHadithPage(null, 100);

        CapturedRequest req = captured.poll();
        assertThat(req.path()).isEqualTo("/api/reactivesearchproxy/es-prod-euw1-hadith-12-read/_search");
        assertThat(req.headers().get("Origin").get(0)).isEqualTo("https://alminasa.ai");
        assertThat(req.headers().get("Referer").get(0)).isEqualTo("https://alminasa.ai/");
        assertThat(req.headers().get("Content-type").get(0)).isEqualTo("application/json");

        JsonNode body = MAPPER.readTree(req.body());
        assertThat(body.path("size").asInt()).isEqualTo(100);
        assertThat(body.path("sort").get(0).path("hadith_serial_id").path("order").asText())
                .isEqualTo("asc");
        assertThat(body.path("track_total_hits").asBoolean()).isTrue();
        assertThat(body.has("search_after")).isFalse();

        assertThat(page.totalHits()).isEqualTo(21);
        assertThat(page.hits()).hasSize(2);
        AlminasaHit first = page.hits().get(0);
        assertThat(first.id()).isEqualTo("146-1");
        assertThat(first.source().path("hadith_id").asText()).isEqualTo("146-1");
    }

    @Test
    void fetchHadithPage_resume_передаёт_search_after() throws IOException {
        fixtureToServe.set("hadith-page-empty.json");

        AlminasaPage page = client.fetchHadithPage(4242L, 50);

        JsonNode body = MAPPER.readTree(captured.poll().body());
        assertThat(body.path("search_after").get(0).asLong()).isEqualTo(4242L);
        assertThat(page.hits()).isEmpty();
    }

    @Test
    void fetchNarratorsByIds_terms_по_id() throws IOException {
        fixtureToServe.set("narrators.json");

        List<AlminasaHit> hits = client.fetchNarratorsByIds(List.of(5719L, 4698L));

        CapturedRequest req = captured.poll();
        assertThat(req.path()).isEqualTo("/api/reactivesearchproxy/es-prod-euw1-narrators-12-read/_search");
        JsonNode body = MAPPER.readTree(req.body());
        JsonNode terms = body.path("query").path("terms").path("id");
        assertThat(terms.get(0).asLong()).isEqualTo(5719L);
        assertThat(body.path("size").asInt()).isEqualTo(2);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).id()).isEqualTo("5719");
    }

    @Test
    void fetchExplanationsByHadithIds_вложенный_terms() throws IOException {
        fixtureToServe.set("explanations.json");

        AlminasaPage page = client.fetchExplanationsByHadithIds(List.of("146-1"));

        CapturedRequest req = captured.poll();
        assertThat(req.path())
                .isEqualTo("/api/reactivesearchproxy/es-prod-euw1-hadith-explanation-12-read/_search");
        JsonNode body = MAPPER.readTree(req.body());
        assertThat(body.path("query").path("terms").path("hadith.hadith_id").get(0).asText())
                .isEqualTo("146-1");
        assertThat(body.path("size").asInt()).isEqualTo(500);

        assertThat(page.hits().get(0).source().path("hadith").path("hadith_id").asText())
                .isEqualTo("146-1");
    }

    @Test
    void fetchRulingsByHadithIds_terms_по_hadith_id() throws IOException {
        fixtureToServe.set("rulings.json");

        AlminasaPage page = client.fetchRulingsByHadithIds(List.of("146-1"));

        CapturedRequest req = captured.poll();
        assertThat(req.path())
                .isEqualTo("/api/reactivesearchproxy/es-prod-euw1-rulings-12_v2-read/_search");
        JsonNode body = MAPPER.readTree(req.body());
        assertThat(body.path("query").path("terms").path("hadith_id").get(0).asText())
                .isEqualTo("146-1");

        assertThat(page.hits().get(0).source().path("ruler").asText()).isEqualTo("البخاري");
    }

    @Test
    void не_2xx_бросает_AlminasaApiException_со_статусом() {
        fixtureToServe.set("hadith-page-empty.json");
        statusToServe.set(503);

        assertThatThrownBy(() -> client.fetchHadithPage(null, 10))
                .isInstanceOf(AlminasaApiException.class)
                .satisfies(e -> assertThat(((AlminasaApiException) e).statusCode()).isEqualTo(503));
    }

    @Test
    void пустые_коллекции_не_делают_сетевых_вызовов() {
        assertThat(client.fetchNarratorsByIds(List.of())).isEmpty();
        assertThat(client.fetchRulingsByHadithIds(List.of()).hits()).isEmpty();
        assertThat(captured).isEmpty();
    }
}
