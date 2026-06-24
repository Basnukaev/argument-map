package ru.basnukaev.argumentmap.hadith.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;

/**
 * IT data-health endpoint хадис-корпуса (P1-2): счётчики недозаполненных
 * записей + ADMIN-гейт (анон→401, не-ADMIN→403). Класс {@code @Transactional}
 * — каждый тест откатывается, фикстуры не мешают друг другу и продовому
 * seed'у в общей testcontainers-БД (счётчики проверяем дельтой от baseline,
 * не абсолютным значением, т.к. dev-seeder мог насыпать строк).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithHealthControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("hh-admin", UserRole.ADMIN);
        userId = insertUser("hh-user", UserRole.USER);
    }

    // ── ADMIN-гейт ────────────────────────────────────────────────────────────

    @Test
    void health_без_principal_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/hadith/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void health_не_админом_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/hadith/health")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    // ── счётчики ──────────────────────────────────────────────────────────────

    @Test
    void health_админом_200_со_всеми_метриками() throws Exception {
        mockMvc.perform(get("/api/v1/admin/hadith/health")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHadiths").isNumber())
                .andExpect(jsonPath("$.hadithsNullAuthenticity").isNumber())
                .andExpect(jsonPath("$.hadithsWithoutSanad").isNumber())
                .andExpect(jsonPath("$.hadithsWithoutMatn").isNumber())
                .andExpect(jsonPath("$.hadithsNullCollection").isNumber())
                .andExpect(jsonPath("$.totalNarrators").isNumber())
                .andExpect(jsonPath("$.narratorsNullTabaqa").isNumber())
                .andExpect(jsonPath("$.narratorsUnknownReliability").isNumber())
                .andExpect(jsonPath("$.narratorsNullGradeText").isNumber());
    }

    @Test
    void health_считает_недозаполненные_хадисы() throws Exception {
        Baseline before = readHealth();

        UUID collectionId = insertCollection("hh-coll");

        // Хадис A: полностью заполнен — authenticity, collection, есть sanad + matn
        UUID hadithA = insertHadith("SAHIH", collectionId);
        insertSanad(hadithA);
        insertMatn(hadithA, collectionId);

        // Хадис B: authenticity NULL, collection NULL, без sanad, без matn
        insertHadith(null, null);

        Baseline after = readHealth();

        // total +2; null_authenticity +1 (B); without_sanad +1 (B); without_matn +1 (B);
        // null_collection +1 (B). Хадис A ни в одну дыру не попадает.
        org.assertj.core.api.Assertions.assertThat(after.totalHadiths - before.totalHadiths).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(after.nullAuthenticity - before.nullAuthenticity).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(after.withoutSanad - before.withoutSanad).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(after.withoutMatn - before.withoutMatn).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(after.nullCollection - before.nullCollection).isEqualTo(1);
    }

    @Test
    void health_считает_недозаполненных_рави() throws Exception {
        Baseline before = readHealth();

        // Рави A: полностью заполнен — tabaqa, grade_text, reliability_grade=THIQA
        insertNarrator("hh-rawi-a", "5", "THIQA", "ثقة");
        // Рави B: tabaqa NULL, reliability_grade=UNKNOWN, grade_text NULL
        insertNarrator("hh-rawi-b", null, "UNKNOWN", null);
        // Рави C: reliability_grade NULL (тоже считается unknownReliability)
        insertNarrator("hh-rawi-c", "3", null, "صدوق");

        Baseline after = readHealth();

        // total +3; null_tabaqa +1 (B); unknown_reliability +2 (B='UNKNOWN', C=NULL);
        // null_grade_text +1 (B).
        org.assertj.core.api.Assertions.assertThat(after.totalNarrators - before.totalNarrators).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(after.nullTabaqa - before.nullTabaqa).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(after.unknownReliability - before.unknownReliability).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(after.nullGradeText - before.nullGradeText).isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record Baseline(
            long totalHadiths, long nullAuthenticity, long withoutSanad,
            long withoutMatn, long nullCollection,
            long totalNarrators, long nullTabaqa, long unknownReliability,
            long nullGradeText) {
    }

    private Baseline readHealth() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/hadith/health")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return new Baseline(
                node.path("totalHadiths").asLong(),
                node.path("hadithsNullAuthenticity").asLong(),
                node.path("hadithsWithoutSanad").asLong(),
                node.path("hadithsWithoutMatn").asLong(),
                node.path("hadithsNullCollection").asLong(),
                node.path("totalNarrators").asLong(),
                node.path("narratorsNullTabaqa").asLong(),
                node.path("narratorsUnknownReliability").asLong(),
                node.path("narratorsNullGradeText").asLong());
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }

    private UUID insertCollection(String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO hd_collections (id, slug, name_ar) VALUES (?, ?, ?)",
                id, slug + "-" + id, "اسم");
        return id;
    }

    private UUID insertHadith(String authenticity, UUID collectionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO hd_hadiths (id, collection_id, normalized_matn, status, authenticity) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id, collectionId, "متن " + id, "VARIANT", authenticity);
        return id;
    }

    private void insertSanad(UUID hadithId) {
        jdbcTemplate.update(
                "INSERT INTO hd_sanads (id, hadith_id, primary_chain) VALUES (?, ?, ?)",
                UUID.randomUUID(), hadithId, true);
    }

    private void insertMatn(UUID hadithId, UUID collectionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO hd_matns (id, hadith_id, text_ar, text_ar_normalized, collection_id, is_primary) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, hadithId, "نص " + id, "نص " + id, collectionId, true);
    }

    private void insertNarrator(String suffix, String tabaqa, String reliabilityGrade, String gradeText) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO hd_narrators (id, name_ar, name_ar_normalized, "
                        + "reliability_grade, tabaqa, grade_text) VALUES (?, ?, ?, ?, ?, ?)",
                id, suffix + "-" + id, suffix + "-" + id, reliabilityGrade, tabaqa, gradeText);
    }
}
