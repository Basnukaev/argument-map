package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpExchange;
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
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmAmbiguousStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCommentaryStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaDependentsBackfillService.Status;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaBackfillConflictException;

/**
 * IT backfill-краула علل/غريب на stub-сервере (фикстуры s59): end-to-end проход
 * (commentary + ambiguous застейджены, чекпоинт COMPLETED), фейл→IDLE+error→
 * relaunch, conflict при живом RUNNING. Синтетический hadith-raw с {@code ambiguous[]}
 * сидируется в am_staging_hadith.
 *
 * <p>БЕЗ @Transactional: backfill коммитит upsert'ы по ходу — чистим руками.
 */
@SpringBootTest(properties = {
        "alminasa.crawl.delay-ms=0",
        "alminasa.crawl.page-size=100",
        "alminasa.crawl.dependent-batch-size=25",
        "alminasa.crawl.dependent-fetch-size=500"
})
@Import(TestcontainersConfiguration.class)
class AlminasaDependentsBackfillServiceIT {

    private static HttpServer server;
    private static final ConcurrentLinkedQueue<String> commentaryRequests = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<String> ambiguousRequests = new ConcurrentLinkedQueue<>();
    /** Управляемый HTTP-статус стаба (для фейл-теста). */
    private static final AtomicInteger statusToServe = new AtomicInteger(200);
    /** Латч, на котором зависает commentary-handler (для conflict-теста); null = не ждать. */
    private static final AtomicReference<CountDownLatch> commentaryGate = new AtomicReference<>();
    /** Сигнал, что commentary-handler стартовал (воркер точно в RUNNING). */
    private static final AtomicReference<CountDownLatch> commentaryEntered = new AtomicReference<>();

    @Autowired private AlminasaDependentsBackfillService backfillService;
    @Autowired private AmHadithStagingDao hadithDao;
    @Autowired private AmCommentaryStagingDao commentaryDao;
    @Autowired private AmAmbiguousStagingDao ambiguousDao;
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

    private static synchronized int ensureStub() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось создать stub HttpServer", e);
        }
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-hadith-commentary-12-read/_search",
                exchange -> {
                    commentaryRequests.add(readBody(exchange));
                    CountDownLatch entered = commentaryEntered.get();
                    if (entered != null) {
                        entered.countDown();
                    }
                    CountDownLatch gate = commentaryGate.get();
                    if (gate != null) {
                        try {
                            gate.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    serveFixture(exchange, "s59/commentary-search-response.json");
                });
        server.createContext("/api/reactivesearchproxy/es-prod-euw1-ambiguous-12-read/_search",
                exchange -> {
                    ambiguousRequests.add(readBody(exchange));
                    serveFixture(exchange, "s59/ambiguous-search-response.json");
                });
        server.start();
        return server.getAddress().getPort();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void serveFixture(HttpExchange exchange, String name) throws IOException {
        int status = statusToServe.get();
        if (status != 200) {
            byte[] err = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, err.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(err);
            }
            return;
        }
        byte[] body;
        try (InputStream in = AlminasaDependentsBackfillServiceIT.class
                .getResourceAsStream("/alminasa/" + name)) {
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
        statusToServe.set(200);
        commentaryGate.set(null);
        commentaryEntered.set(null);
        commentaryRequests.clear();
        ambiguousRequests.clear();
        jdbcTemplate.update("DELETE FROM am_staging_commentary");
        jdbcTemplate.update("DELETE FROM am_staging_ambiguous");
        jdbcTemplate.update("DELETE FROM am_staging_hadith");
        jdbcTemplate.update("DELETE FROM am_crawl_checkpoint");
    }

    /** Синтетический хадис 146-2 с непустым {@code ambiguous[]} (источник id'ов гариба). */
    private void seedHadithWithAmbiguous() {
        String raw = "{\"hadith_id\":\"146-2\",\"book_name\":\"صحيح البخاري\","
                + "\"ambiguous\":[{\"reference\":\"جرس\",\"reference_id\":12,"
                + "\"explanation_ids\":[760182,770632]}]}";
        hadithDao.upsertAll(List.of(new AmHadithRow(
                "146-2", 146, 2L, "صحيح البخاري", "مرفوع", null, null, raw)));
    }

    @Test
    void полный_проход_стейджит_commentary_и_ambiguous_и_завершает_чекпоинт() {
        seedHadithWithAmbiguous();

        // синхронный цикл — детерминизм без ожидания executor'а
        checkpointDao.upsertRunning(AlminasaDependentsBackfillService.BACKFILL_INDEX_KEY, true);
        backfillService.backfillLoop();

        assertThat(commentaryDao.count()).isEqualTo(1);
        assertThat(ambiguousDao.count()).isEqualTo(2);
        // запрос commentary нёс наш hadith_id, ambiguous — оба explanation_ids
        assertThat(commentaryRequests).anySatisfy(r -> assertThat(r).contains("146-2"));
        assertThat(ambiguousRequests).anySatisfy(r ->
                assertThat(r).contains("760182").contains("770632"));

        assertThat(checkpointDao.find(AlminasaDependentsBackfillService.BACKFILL_INDEX_KEY)
                .orElseThrow().status()).isEqualTo(AmCrawlStatus.COMPLETED);
    }

    @Test
    void хадис_без_ambiguous_только_commentary() {
        // raw без поля ambiguous → ни одного ambiguous-запроса
        hadithDao.upsertAll(List.of(new AmHadithRow(
                "146-3", 146, 3L, "صحيح البخاري", "مرفوع", null, null,
                "{\"hadith_id\":\"146-3\"}")));

        checkpointDao.upsertRunning(AlminasaDependentsBackfillService.BACKFILL_INDEX_KEY, true);
        backfillService.backfillLoop();

        assertThat(commentaryDao.count()).isEqualTo(1); // стаб всегда отдаёт 1 коммент
        assertThat(ambiguousDao.count()).isZero();
        assertThat(ambiguousRequests).isEmpty();
    }

    @Test
    void фейл_апи_уводит_state_в_IDLE_с_error_и_позволяет_relaunch() {
        seedHadithWithAmbiguous();
        statusToServe.set(503);

        backfillService.start();
        awaitUntil(() -> backfillService.status().status() == Status.IDLE
                && backfillService.status().lastError() != null);

        assertThat(checkpointDao.find(AlminasaDependentsBackfillService.BACKFILL_INDEX_KEY)
                .orElseThrow().status()).isEqualTo(AmCrawlStatus.FAILED);

        // relaunch после фикса апи — 202 (start не бросает), доходит до COMPLETED
        statusToServe.set(200);
        backfillService.start();
        awaitUntil(() -> checkpointDao.find(AlminasaDependentsBackfillService.BACKFILL_INDEX_KEY)
                .orElseThrow().status() == AmCrawlStatus.COMPLETED);
        assertThat(commentaryDao.count()).isEqualTo(1);
        assertThat(ambiguousDao.count()).isEqualTo(2);
    }

    @Test
    void повторный_start_при_живом_RUNNING_конфликтует_потом_снова_можно()
            throws InterruptedException {
        seedHadithWithAmbiguous();
        // зависим первый воркер в commentary-handler'е → state остаётся RUNNING
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        commentaryGate.set(gate);
        commentaryEntered.set(entered);

        backfillService.start();
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue(); // воркер точно в RUNNING

        // второй start пока первый держит RUNNING → 409
        assertThatThrownBy(() -> backfillService.start())
                .isInstanceOf(AlminasaBackfillConflictException.class);
        assertThat(backfillService.status().status()).isEqualTo(Status.RUNNING);

        // отпускаем воркер → завершение → state IDLE → start снова разрешён
        gate.countDown();
        awaitUntil(() -> backfillService.status().status() == Status.IDLE);

        commentaryGate.set(null);
        commentaryEntered.set(null);
        backfillService.start();
        awaitUntil(() -> checkpointDao.find(AlminasaDependentsBackfillService.BACKFILL_INDEX_KEY)
                .orElseThrow().status() == AmCrawlStatus.COMPLETED);
    }

    /** Поллинг условия до 10s (паттерн awaitStatus импорт-IT, без Awaitility-зависимости). */
    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("ожидание прервано", e);
            }
        }
        throw new IllegalStateException("условие не выполнилось за 10s");
    }
}
