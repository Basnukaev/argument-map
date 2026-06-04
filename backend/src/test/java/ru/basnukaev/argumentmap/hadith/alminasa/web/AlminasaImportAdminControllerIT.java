package ru.basnukaev.argumentmap.hadith.alminasa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaImportService;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;

/**
 * IT admin-endpoints импорта alminasa (План 5, T1): каталог 12 строк (staged/
 * mapped прогресс, alminasa-only mappedCount), async-импорт narrators/hadiths
 * (202/409/finally-контракт), dry-run превью (200/404/422), ADMIN-гейт.
 *
 * <p>Класс <b>БЕЗ</b> {@code @Transactional}: импорт-эндпоинты запускают
 * async-поток с собственными транзакциями маппера; изоляция — ручная чистка
 * staging+hd_* в {@link #cleanup()}. 409 проверяется ТОЛЬКО latch-вариантом
 * (занимаем единственный поток executor'а), «маленький быстрый датасет»
 * флакал бы. {@code @MockitoSpyBean AlminasaImportService} делегирует в
 * реальный бин по умолчанию (catalog/dry-run/scope тесты — настоящие);
 * стабится ТОЛЬКО для finally-контракта (импорт-фейл → IDLE+lastError).
 */
@SpringBootTest(properties = {
        "alminasa.base-url=http://127.0.0.1:1",
        "alminasa.crawl.delay-ms=0"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlminasaImportAdminControllerIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AmHadithStagingDao hadithStagingDao;
    @Autowired private CollectionRepository collectionRepository;
    @Autowired private HadithRepository hadithRepository;
    @Autowired @Qualifier("alminasaImportExecutor") private ThreadPoolTaskExecutor importExecutor;
    @MockitoSpyBean private AlminasaImportService importService;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() throws InterruptedException {
        awaitImportExecutorIdle();
        cleanup();
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'amimp-%'");
        adminId = insertUser("amimp-admin", UserRole.ADMIN);
        userId = insertUser("amimp-user", UserRole.USER);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadith_editions");
        jdbcTemplate.update("DELETE FROM hd_hadith_crossrefs");
        jdbcTemplate.update("DELETE FROM hd_rulings");
        jdbcTemplate.update("DELETE FROM hd_explanations");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
        jdbcTemplate.update("DELETE FROM hd_narrator_relations");
        jdbcTemplate.update("DELETE FROM hd_narrators");
        jdbcTemplate.update("DELETE FROM hd_collections");
        jdbcTemplate.update("DELETE FROM am_staging_explanation");
        jdbcTemplate.update("DELETE FROM am_staging_ruling");
        jdbcTemplate.update("DELETE FROM am_staging_narrator");
        jdbcTemplate.update("DELETE FROM am_staging_hadith");
    }

    // ── ADMIN-гейт ────────────────────────────────────────────────────────────

    @Test
    void catalog_без_principal_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importNarrators_не_админом_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/import/narrators")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    // ── каталог ─────────────────────────────────────────────────────────────

    @Test
    void catalog_пустой_staging_12_строк_с_нулями() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/catalog")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[?(@.slug=='bukhari')].stagedCount").value(0))
                .andExpect(jsonPath("$[?(@.slug=='bukhari')].mappedCount").value(0))
                .andExpect(jsonPath("$[?(@.slug=='bukhari')].bookId").value(146));
    }

    @Test
    void catalog_смешанная_коллекция_mappedCount_только_alminasa() throws Exception {
        // staging: 1 alminasa-док в bukhari → stagedCount=1
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-1", 146, 1L, "صحيح البخاري", "مرفوع", null, null,
                "{\"hadith_id\":\"146-1\"}")));
        // коллекция bukhari + 2 хадиса: один alminasa, один sunnah
        UUID collectionId = UUID.randomUUID();
        collectionRepository.save(new Collection(
                collectionId, "bukhari", "صحيح البخاري", null, "Сахих аль-Бухари",
                null, null, "{}", java.time.Instant.now(), null));
        hadithRepository.save(hadith(collectionId, "alminasa", "146-1"));
        hadithRepository.save(hadith(collectionId, "sunnah", "bukhari:1"));

        mockMvc.perform(get("/api/v1/admin/alminasa/catalog")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[?(@.slug=='bukhari')].stagedCount").value(1))
                // mappedCount считает ТОЛЬКО alminasa-хадис (фикс C1), sunnah-строка не входит
                .andExpect(jsonPath("$[?(@.slug=='bukhari')].mappedCount").value(1));
    }

    // ── статус + happy импорт ─────────────────────────────────────────────────

    @Test
    void importStatus_до_запуска_IDLE() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/import/status")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
    }

    @Test
    void importNarrators_happy_202_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/import/narrators")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.kind").value("NARRATORS"));
        awaitImportExecutorIdle();
    }

    @Test
    void importHadiths_happy_202_со_статусом() throws Exception {
        mockMvc.perform(post("/api/v1/admin/alminasa/import/hadiths")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.kind").value("HADITHS"));
        awaitImportExecutorIdle();
    }

    @Test
    void importHadiths_bookId_скоупит_импорт_одним_сборником() throws Exception {
        // два сборника в staging: bukhari (146) и muslim (158)
        seedHadithFixture("146-1", 146, 1L);
        seedHadithFixture("158-1", 158, 1L);

        mockMvc.perform(post("/api/v1/admin/alminasa/import/hadiths")
                        .header("X-User-Id", adminId.toString())
                        .param("bookId", "146"))
                .andExpect(status().isAccepted());
        awaitImportExecutorIdle();

        // импортирован только bukhari-док, muslim — нет (контентный фильтр поверх keyset)
        assertThat(hadithRepository.findByExternalId("alminasa", "146-1")).isPresent();
        assertThat(hadithRepository.findByExternalId("alminasa", "158-1")).isEmpty();
    }

    // ── 409 (ТОЛЬКО latch) ────────────────────────────────────────────────────

    @Test
    void двойной_запуск_409_через_latch() throws Exception {
        // занимаем единственный поток executor'а «живым» воркером — submit отобьётся.
        // started-latch гарантирует что задача УЖЕ на потоке к моменту запуска
        // (иначе queue=0 ещё свободен и submit прошёл бы — гонка).
        awaitImportExecutorIdle();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        importExecutor.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        try {
            // первый launch: CAS IDLE→RUNNING проходит, submit отбивается AbortPolicy
            // (поток занят latch) → TaskRejected → откат IDLE + 409.
            mockMvc.perform(post("/api/v1/admin/alminasa/import/narrators")
                            .header("X-User-Id", adminId.toString()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type", containsString("alminasa-import-already-running")));
        } finally {
            release.countDown();
            awaitImportExecutorIdle();
        }
    }

    // ── finally-контракт: фейл → IDLE+lastError → повторный 202 ────────────────

    @Test
    void импорт_фейл_оставляет_IDLE_с_lastError_и_повторный_запуск_202() throws Exception {
        // спай стабится бросать → async-тело ловит RuntimeException → IDLE+lastError
        doThrow(new RuntimeException("boom"))
                .when(importService).importNarrators(any(IntConsumer.class));

        mockMvc.perform(post("/api/v1/admin/alminasa/import/narrators")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted());
        awaitImportExecutorIdle();
        awaitStatus("IDLE");

        mockMvc.perform(get("/api/v1/admin/alminasa/import/status")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.error", containsString("boom")));

        // повторный запуск НЕ 409 (state вышел из RUNNING) — finally-контракт держит
        mockMvc.perform(post("/api/v1/admin/alminasa/import/narrators")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isAccepted());
        awaitImportExecutorIdle();
    }

    // ── dry-run 200/404/422 ───────────────────────────────────────────────────

    @Test
    void dryRun_застейдженный_хадис_200_с_цепью() throws Exception {
        JsonNode src = fixture("hadith-page.json").path("hits").path("hits").get(0).path("_source");
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-1", 146, 1L, "صحيح البخاري", "مرفوع",
                "باب بدء الوحي", null, src.toString())));

        mockMvc.perform(get("/api/v1/admin/alminasa/dry-run/146-1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("146-1"))
                .andExpect(jsonPath("$.collectionSlug").value("bukhari"))
                .andExpect(jsonPath("$.chain.length()").value(6))
                .andExpect(jsonPath("$.chain[0].position").value(0));

        // dry-run недеструктивен: ничего не закоммичено
        assertThat(hadithRepository.findByExternalId("alminasa", "146-1")).isEmpty();
    }

    @Test
    void dryRun_нестейдженный_id_404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/alminasa/dry-run/999-999")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", containsString("alminasa-staging-not-found")));
    }

    @Test
    void dryRun_застейджен_но_пустой_матн_422() throws Exception {
        // застейджен, но без matn_with_tashkeel → AlminasaMappingException → 422
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-998", 146, 998L, "صحيح البخاري", "مرفوع", null, null,
                "{\"hadith_id\":\"146-998\",\"book_name\":\"صحيح البخاري\","
                        + "\"type\":\"مرفوع\",\"hadith\":\"نص\",\"number\":[998]}")));

        mockMvc.perform(get("/api/v1/admin/alminasa/dry-run/146-998")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", containsString("alminasa-mapping-failed")));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedHadithFixture(String hadithId, int bookId, long serial) throws IOException {
        JsonNode src = fixture("hadith-page.json").path("hits").path("hits").get(0).path("_source");
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                hadithId, bookId, serial, "صحيح البخاري", "مرفوع", null, null, src.toString())));
    }

    private static Hadith hadith(UUID collectionId, String source, String externalId) {
        return new Hadith(
                UUID.randomUUID(), collectionId, null, "متن " + externalId,
                HadithStatus.CANONICAL, null, "{}", java.time.Instant.now(),
                source, externalId, null, null, null, null);
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + name)) {
            return MAPPER.readTree(in);
        }
    }

    private void awaitStatus(String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String body = mockMvc.perform(get("/api/v1/admin/alminasa/import/status")
                            .header("X-User-Id", adminId.toString()))
                    .andReturn().getResponse().getContentAsString();
            if (MAPPER.readTree(body).path("status").asText().equals(expected)) {
                return;
            }
            Thread.sleep(30);
        }
        throw new IllegalStateException("status не стал " + expected + " за 10s");
    }

    private void awaitImportExecutorIdle() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (importExecutor.getActiveCount() > 0) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("import-executor не освободился за 30s");
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
