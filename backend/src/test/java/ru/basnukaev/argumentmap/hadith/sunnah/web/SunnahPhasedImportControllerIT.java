package ru.basnukaev.argumentmap.hadith.sunnah.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDataSource;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDumpReader;

/**
 * IT фазового/верифицируемого импорта sunnah.com через HTTP-контроллер
 * (ADR-052): browse источника, DRY-RUN preview (без записи в БД), импорт
 * одного хадиса. Двухконтейнерный (зеркалит {@code SunnahImportServiceIT}):
 * Postgres (наша БД) + MySQL (дамп, fixture {@code sunnah/sample-schema.sql}).
 *
 * <p>{@link DumpSource} регистрирует {@link SunnahDataSource}-бин поверх MySQL-
 * контейнера — в обычном профиле его нет (gate {@code sunnah.dump.enabled}),
 * здесь он эмулирует сконфигурированный источник, чтобы контроллер прошёл за
 * 503-gate. ADMIN-авторизация и сам 503-gate покрыты {@code
 * SunnahAdminControllerIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SunnahPhasedImportControllerIT.DumpSource.class})
@Testcontainers
class SunnahPhasedImportControllerIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("sunnah/sample-schema.sql");

    @TestConfiguration
    static class DumpSource {
        @Bean
        SunnahDataSource sunnahDataSource(ObjectMapper objectMapper) {
            DriverManagerDataSource ds = new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            ds.setDriverClassName(mysql.getDriverClassName());
            return new SunnahDumpReader(ds, objectMapper);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
        jdbcTemplate.update("DELETE FROM hd_collections");
        jdbcTemplate.update("DELETE FROM hd_narrators");
        jdbcTemplate.update("DELETE FROM sn_staging_hadith");
        jdbcTemplate.update("DELETE FROM sn_staging_chapter");
        jdbcTemplate.update("DELETE FROM sn_staging_book");
        jdbcTemplate.update("DELETE FROM sn_staging_collection");
        adminId = insertUser(UserRole.ADMIN);
    }

    // ---- 0. list collections — availableHadith ----

    @Test
    void list_collections_availableHadith_reflects_actual_HadithTable_rows() throws Exception {
        // Фикстура: 4 хадиса bukhari, 0 muslim → bukhari.availableHadith=4,
        // muslim.availableHadith=0; totalHadith — каталожный (не трогаем)
        mockMvc.perform(get("/api/v1/admin/sunnah/collections")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'bukhari')].availableHadith")
                        .value(org.hamcrest.Matchers.contains(4)))
                .andExpect(jsonPath("$[?(@.name == 'muslim')].availableHadith")
                        .value(org.hamcrest.Matchers.contains(0)))
                // totalHadith каталожный (из Collections таблицы) — не совпадает с actual
                .andExpect(jsonPath("$[?(@.name == 'bukhari')].totalHadith")
                        .value(org.hamcrest.Matchers.contains(7563)));
    }

    // ---- 1. browse источника ----

    @Test
    void browse_returns_paged_dump_hadiths_with_alreadyImported_false_when_nothing_imported()
            throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/collections/bukhari/hadiths")
                        .param("page", "0").param("size", "2")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].number").exists())
                .andExpect(jsonPath("$.items[0].alreadyImported").value(false))
                .andExpect(jsonPath("$.items[0].textArSnippet").exists());
    }

    @Test
    void browse_reflects_alreadyImported_true_after_single_import() throws Exception {
        // импортируем хадис №1, затем browse должен пометить его alreadyImported
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/sunnah/collections/bukhari/hadiths")
                        .param("size", "100")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                // хадис "1" импортирован → true; остальные → false
                .andExpect(jsonPath("$.items[?(@.number == '1')].alreadyImported")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.items[?(@.number == '2')].alreadyImported")
                        .value(org.hamcrest.Matchers.contains(false)));
    }

    // ---- 2. DRY-RUN preview (БЕЗ записи в БД) ----

    @Test
    void preview_returns_mapped_fields_without_creating_hd_hadiths_row() throws Exception {
        long before = count("hd_hadiths");

        mockMvc.perform(get("/api/v1/admin/sunnah/preview/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection").value("bukhari"))
                .andExpect(jsonPath("$.primaryNumber").value(1))
                .andExpect(jsonPath("$.status").value("VARIANT"))
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.alreadyImported").value(false))
                // очищенный текст ar/en + нормализованный matn (как у реального импорта)
                .andExpect(jsonPath("$.matnEn").value("Actions are by intentions"))
                .andExpect(jsonPath("$.normalizedMatn").value("انما الاعمال بالنيات"))
                // grades распарсены [{scholar, grade}]
                .andExpect(jsonPath("$.grades[0].grade").value("Sahih"))
                // структура книга/глава из метаданных
                .andExpect(jsonPath("$.structure.bookNameEn").value("Revelation"))
                .andExpect(jsonPath("$.structure.chapterTitleEn")
                        .value("How the Divine Revelation started"))
                // иснад пока не выводится
                .andExpect(jsonPath("$.isnad").doesNotExist());

        // ключевое: preview НЕ записал ничего в БД (rollback)
        long after = count("hd_hadiths");
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThat(count("hd_matns")).isZero();
        org.assertj.core.api.Assertions.assertThat(count("hd_collections")).isZero();
        org.assertj.core.api.Assertions.assertThat(count("sn_staging_hadith")).isZero();
    }

    @Test
    void preview_unknown_hadith_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/preview/bukhari/99999")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", containsString("sunnah-hadith-not-found")));
    }

    // ---- 3. import одного хадиса (фазовый) ----

    @Test
    void single_import_creates_exactly_one_row_and_is_idempotent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.skippedExisting").value(0));

        org.assertj.core.api.Assertions.assertThat(count("hd_hadiths")).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("hd_matns")).isEqualTo(1);

        // повтор → идемпотентно: ничего не вставлено, та же одна строка
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.skippedExisting").value(1));

        org.assertj.core.api.Assertions.assertThat(count("hd_hadiths")).isEqualTo(1);
    }

    @Test
    void single_import_unknown_hadith_returns_404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/99999")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", containsString("sunnah-hadith-not-found")));
    }

    @Test
    void preview_after_real_import_marks_alreadyImported_true() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk());
        long imported = count("hd_hadiths");

        mockMvc.perform(get("/api/v1/admin/sunnah/preview/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported").value(true))
                .andExpect(jsonPath("$.matnEn").value("Actions are by intentions"));

        // preview не тронул уже импортированную строку
        org.assertj.core.api.Assertions.assertThat(count("hd_hadiths")).isEqualTo(imported);
    }

    @Test
    void browse_size_over_max_does_not_break_and_returns_all() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/collections/bukhari/hadiths")
                        .param("size", "10000")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(4)));
    }

    private long count(String table) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0L : n;
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, "user-" + id, id + "@test.com", role);
        return id;
    }
}
