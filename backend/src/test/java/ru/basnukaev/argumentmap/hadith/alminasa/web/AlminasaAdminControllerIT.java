package ru.basnukaev.argumentmap.hadith.alminasa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmCrawlCheckpointDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCrawlService;

/**
 * IT admin-endpoints краулера alminasa: ADMIN-only, 202 на start,
 * 409 на двойной старт, форма status-ответа. base-url указывает на
 * закрытый порт — async-краулер быстро падает в FAILED, на контракт
 * endpoint'ов это не влияет (детерминированный happy-path краулера —
 * в AlminasaCrawlServiceIT).
 *
 * <p>БЕЗ @Transactional: start запускает async-поток с собственными
 * транзакциями чекпоинта — изоляция через ручную чистку в setUp.
 */
@SpringBootTest(properties = {
        "alminasa.base-url=http://127.0.0.1:1",
        "alminasa.crawl.delay-ms=0"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlminasaAdminControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AmCrawlCheckpointDao checkpointDao;
    @Autowired @Qualifier("alminasaCrawlExecutor") private ThreadPoolTaskExecutor crawlExecutor;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM am_crawl_checkpoint");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'alminasa-%'");
        adminId = insertUser("alminasa-admin", UserRole.ADMIN);
        userId = insertUser("alminasa-user", UserRole.USER);
    }

    @Test
    void start_не_админом_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void status_без_чекпоинта_отдаёт_IDLE_и_нулевые_счётчики() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/crawl/status")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.stagedHadiths").value(0))
                .andExpect(jsonPath("$.stagedNarrators").value(0))
                .andExpect(jsonPath("$.stagedExplanations").value(0))
                .andExpect(jsonPath("$.stagedRulings").value(0));
    }

    @Test
    void start_отдаёт_202_со_статусом() throws Exception {
        // shared single-thread executor: краулер прошлого теста мог ещё
        // ретраить закрытый порт — иначе submit отбился бы в 409 (см.
        // start_при_живом_воркере...). Ждём свободный слот → 202.
        awaitCrawlExecutorIdle();
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void двойной_start_409() throws Exception {
        checkpointDao.upsertRunning(AlminasaCrawlService.HADITH_INDEX_KEY, true);

        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", containsString("alminasa-crawl-already-running")));
    }

    @Test
    void start_при_живом_воркере_и_stale_клейме_отдаёт_409() throws Exception {
        // executor — single-thread с shared context: дождаться пока
        // утихнет краулер от предыдущего теста (он ретраит закрытый порт
        // ~6s через alminasaApi-retry), иначе наш submit ниже отобьётся.
        awaitCrawlExecutorIdle();
        // занимаем единственный поток executor'а «живым старым воркером»
        CountDownLatch release = new CountDownLatch(1);
        crawlExecutor.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            // stale RUNNING-клейм — claimStart его перехватит и пойдёт в submit
            checkpointDao.upsertRunning(AlminasaCrawlService.HADITH_INDEX_KEY, true);
            jdbcTemplate.update(
                    "UPDATE am_crawl_checkpoint SET updated_at = now() - interval '1 hour' "
                            + "WHERE index_name = ?", AlminasaCrawlService.HADITH_INDEX_KEY);

            mockMvc.perform(post("/api/v1/admin/alminasa/crawl/start")
                            .header("X-User-Id", adminId.toString()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type", containsString("alminasa-crawl-already-running")));

            // чекпоинт не испорчен: остался RUNNING (им владеет «живой» воркер)
            assertThat(checkpointDao.find(AlminasaCrawlService.HADITH_INDEX_KEY).orElseThrow().status())
                    .isEqualTo(AmCrawlStatus.RUNNING);
        } finally {
            release.countDown();
            // дренируем latch-поток, чтобы не утёк active-thread в следующий тест
            awaitCrawlExecutorIdle();
        }
    }

    @Test
    void pause_отдаёт_200_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/crawl/pause")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    /** Ждёт пока single-thread crawl-executor освободится (краулер прошлого теста утих). */
    private void awaitCrawlExecutorIdle() throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
        while (crawlExecutor.getActiveCount() > 0) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("crawl-executor не освободился за 30s");
            }
            Thread.sleep(50);
        }
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
