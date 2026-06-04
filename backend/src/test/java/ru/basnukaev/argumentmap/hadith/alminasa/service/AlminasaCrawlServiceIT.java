package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaCrawlConflictException;

/**
 * IT краулера на stub-сервере (HAR-фикстуры): полный проход, resume по
 * чекпоинту, pause на границе страницы, conflict при двойном старте,
 * перехват stale RUNNING-claim. {@code crawlLoop} зовётся синхронно —
 * детерминизм без Awaitility; @Async-обёртка тонкая.
 *
 * <p>БЕЗ @Transactional: краулер коммитит upsert'ы по ходу — тест чистит
 * таблицы руками в setUp.
 */
@SpringBootTest(properties = {
        "alminasa.crawl.delay-ms=0",
        "alminasa.crawl.page-size=100",
        "alminasa.crawl.dependent-batch-size=25",
        "alminasa.crawl.stale-timeout-minutes=10"
})
@Import(TestcontainersConfiguration.class)
class AlminasaCrawlServiceIT {

    private static HttpServer server;
    private static final ConcurrentLinkedQueue<String> hadithRequests = new ConcurrentLinkedQueue<>();

    @Autowired private AlminasaCrawlService crawlService;
    @Autowired private AmCrawlCheckpointDao checkpointDao;
    @Autowired private JdbcTemplate jdbcTemplate;

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

    /**
     * Stub: hadith-12 → page1 (2 хита) без search_after, пустая страница с
     * search_after; narrators/explanations/rulings → фикстуры.
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
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-hadith-12-read/_search",
                exchange -> {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    hadithRequests.add(body);
                    serveFixture(exchange,
                            // dup-serial: первая страница — РЕАЛЬНЫЕ доки 121-1/137-1/146-1
                            // (все serial=1; живой урок про per-book serial). Пустая
                            // страница несёт ТОТ ЖЕ total (как реальный ES) — иначе
                            // setTotalHits перетёр бы 82596 на финальной странице.
                            body.contains("search_after")
                                    ? "hadith-page-dup-serial-empty.json"
                                    : "hadith-page-dup-serial.json");
                });
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-narrators-12-read/_search",
                exchange -> serveFixture(exchange, "narrators.json"));
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-hadith-explanation-12-read/_search",
                exchange -> serveFixture(exchange, "explanations.json"));
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-rulings-12_v2-read/_search",
                exchange -> serveFixture(exchange, "rulings.json"));
        server.start();
        return server.getAddress().getPort();
    }

    private static void serveFixture(com.sun.net.httpserver.HttpExchange exchange, String name)
            throws IOException {
        byte[] body;
        try (InputStream in = AlminasaCrawlServiceIT.class.getResourceAsStream("/alminasa/" + name)) {
            body = in.readAllBytes();
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @BeforeEach
    void cleanState() {
        hadithRequests.clear();
        jdbcTemplate.update("DELETE FROM am_staging_hadith");
        jdbcTemplate.update("DELETE FROM am_staging_narrator");
        jdbcTemplate.update("DELETE FROM am_staging_explanation");
        jdbcTemplate.update("DELETE FROM am_staging_ruling");
        jdbcTemplate.update("DELETE FROM am_crawl_checkpoint");
    }

    @Test
    void полный_проход_стейджит_всё_и_завершает_чекпоинт() {
        crawlService.claimStart();
        crawlService.crawlLoop();

        AmCrawlCheckpoint cp = checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow();
        assertThat(cp.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(cp.totalHits()).isEqualTo(82596); // total из live-фикстуры
        // ТРИ дока с ОДИНАКОВЫМ serial=1 — все застейджены (миграция 73)
        assertThat(cp.fetchedCount()).isEqualTo(3);
        assertThat(count("am_staging_hadith")).isEqualTo(3);
        // составной курсор: serial последнего дока + его hadith_id (146-1 — лексикографически последний)
        assertThat(cp.lastSortValue()).isEqualTo(1L);
        assertThat(cp.lastSortId()).isEqualTo("146-1");

        assertThat(count("am_staging_narrator")).isGreaterThanOrEqualTo(1);
        assertThat(count("am_staging_explanation")).isEqualTo(2);
        assertThat(count("am_staging_ruling")).isEqualTo(2);
        // raw — валидный jsonb с полным _source
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw->>'matn_with_tashkeel' FROM am_staging_hadith WHERE hadith_id = '146-1'",
                String.class)).isNotBlank();
        // вторая страница запрошена с СОСТАВНЫМ search_after [1, "146-1"]
        assertThat(hadithRequests).hasSize(2);
        String secondRequest = hadithRequests.stream().skip(1).findFirst().orElseThrow();
        assertThat(secondRequest).contains("search_after");
        assertThat(secondRequest).contains("\"146-1\"");
    }

    @Test
    void resume_продолжает_с_чекпоинта_не_с_нуля() {
        checkpointDao.upsertRunning(AlminasaCrawlService.HADITH_INDEX_KEY, true);
        checkpointDao.advance(AlminasaCrawlService.HADITH_INDEX_KEY, 9999L, "146-9999", 100);
        checkpointDao.markPaused(AlminasaCrawlService.HADITH_INDEX_KEY);

        crawlService.claimStart();
        crawlService.crawlLoop();

        // ПЕРВЫЙ же запрос — с составным search_after:[9999,"146-9999"] → пусто → COMPLETED
        assertThat(hadithRequests).hasSize(1);
        assertThat(hadithRequests.peek()).contains("9999").contains("\"146-9999\"");
        AmCrawlCheckpoint cp = checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow();
        assertThat(cp.status()).isEqualTo(AmCrawlStatus.COMPLETED);
        assertThat(cp.fetchedCount()).isEqualTo(100); // прогресс сохранён
    }

    @Test
    void pause_останавливает_на_границе_страницы() {
        crawlService.claimStart();
        crawlService.pause();
        crawlService.crawlLoop();

        AmCrawlCheckpoint cp = checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow();
        assertThat(cp.status()).isEqualTo(AmCrawlStatus.PAUSED);
        // страница успела застейджиться перед паузой
        assertThat(count("am_staging_hadith")).isEqualTo(3);
        assertThat(hadithRequests).hasSize(1);
    }

    @Test
    void повторный_start_при_живом_RUNNING_конфликтует() {
        crawlService.claimStart();
        assertThatThrownBy(() -> crawlService.claimStart())
                .isInstanceOf(AlminasaCrawlConflictException.class);
    }

    @Test
    void stale_RUNNING_перехватывается() {
        crawlService.claimStart();
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET updated_at = now() - interval '1 hour' "
                        + "WHERE index_name = ?", AlminasaCrawlService.HADITH_INDEX_KEY);

        crawlService.claimStart(); // не бросает
        assertThat(checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow().status())
                .isEqualTo(AmCrawlStatus.RUNNING);
    }

    private long count(String table) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }
}
