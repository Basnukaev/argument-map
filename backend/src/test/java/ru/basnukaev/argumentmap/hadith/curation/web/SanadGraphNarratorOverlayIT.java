package ru.basnukaev.argumentmap.hadith.curation.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;

/**
 * IT курации narrator-overlay в графе иснада (Фаза 5.b): правка поля рави видна
 * в EFFECTIVE sanad-graph всем; field-hide → значение узла null; admin-индикатор
 * {@code overriddenFields} — только ADMIN (reveal), гость получает пустой список.
 * Рави НЕ record-hideable — узел никогда не исчезает (whitelist).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SanadGraphNarratorOverlayIT {

    private static final String OVERRIDES = "/api/v1/admin/curation/overrides";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private NarratorRepository narratorRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = insertUser(UserRole.ADMIN);
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, "u-" + id, id + "@t.com", role);
        return id;
    }

    /** Хадис с одной цепью: сподвижник (position 0) → коллектор. Возвращает id рави-коллектора. */
    private record Fixture(UUID hadithId, UUID companionId, UUID collectorId) {}

    private Fixture newHadithWithChain(String collectorGrade) {
        return newHadithWithChain(collectorGrade, null);
    }

    /**
     * Хадис с цепью сподвижник→коллектор. {@code collectorGradeText} —
     * verbatim джарх рави-коллектора (поле {@code grade_text}, field-hideable).
     */
    private Fixture newHadithWithChain(String collectorGrade, String collectorGradeText) {
        UUID hid = UUID.randomUUID();
        hadithRepository.save(new Hadith(hid, null, 1, "متن", HadithStatus.CANONICAL, null, null, Instant.now()));

        UUID companion = UUID.randomUUID();
        narratorRepository.save(new Narrator(companion, null, "صحابي", "صحابي", null, null, null, null,
                null, null, null, NarratorReliability.SAHABI, null, 0, "{}", Instant.now(),
                null, null, null, null, null, null));
        UUID collector = UUID.randomUUID();
        narratorRepository.save(new Narrator(collector, null, "البخاري", "البخاري", null, null, null, null,
                null, null, null, collectorGrade, null, 0, "{}", Instant.now(),
                null, null, null, collectorGradeText, null, null));

        UUID sanadId = UUID.randomUUID();
        sanadRepository.save(new Sanad(sanadId, hid, "SAHIH", collector, null, true, "{}", Instant.now()));
        sanadRepository.saveNarratorLink(new SanadNarrator(sanadId, 0, companion, "سمعت"));
        sanadRepository.saveNarratorLink(new SanadNarrator(sanadId, 1, collector, "حدثنا"));
        return new Fixture(hid, companion, collector);
    }

    private void edit(String table, UUID entityId, String field, String value) throws Exception {
        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"" + table + "\",\"entityId\":\"" + entityId
                                + "\",\"fieldName\":\"" + field + "\",\"value\":\"" + value
                                + "\",\"reason\":\"фикс\"}"))
                .andExpect(status().isOk());
    }

    private void hideField(String table, UUID entityId, String field) throws Exception {
        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"" + table + "\",\"entityId\":\"" + entityId
                                + "\",\"fieldName\":\"" + field + "\",\"hidden\":true,\"reason\":\"модерация\"}"))
                .andExpect(status().isOk());
    }

    private static String nodeJsonpath(UUID narratorId, String field) {
        return "$.nodes[?(@.id=='narrator-" + narratorId + "')].data." + field;
    }

    // ── EFFECTIVE-значение видно всем ───────────────────────────────────────────

    @Test
    void editReliabilityGrade_effectiveInGraph_forGuest() throws Exception {
        Fixture f = newHadithWithChain(NarratorReliability.DAIF);

        edit("hd_narrators", f.collectorId(), "reliability_grade", "THIQA");

        // гость — без X-User-Id: значение узла EFFECTIVE (DAIF → THIQA),
        // но overriddenFields пуст (admin-индикатор не отдаётся не-ADMIN).
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", f.hadithId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "reliabilityGrade"), contains("THIQA")))
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "overriddenFields"), hasItem(empty())));
    }

    @Test
    void editReliabilityGrade_overriddenFieldsForAdmin() throws Exception {
        Fixture f = newHadithWithChain(NarratorReliability.DAIF);

        edit("hd_narrators", f.collectorId(), "reliability_grade", "THIQA");

        // ADMIN — то же EFFECTIVE-значение + overriddenFields с именем колонки.
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", f.hadithId())
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "reliabilityGrade"), contains("THIQA")))
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "overriddenFields"),
                        hasItem(hasItem("reliability_grade"))));
    }

    // ── field-hide → значение узла null ─────────────────────────────────────────

    @Test
    void hideGradeText_nullInGraph_nodeStillPresent() throws Exception {
        // grade_text — field-hideable у рави (спорный джарх); reliability_grade
        // НЕ hideable (whitelist) — потому скрываем именно verbatim-джарх.
        Fixture f = newHadithWithChain(NarratorReliability.THIQA, "ثقة حافظ");

        hideField("hd_narrators", f.collectorId(), "grade_text");

        // Узел рави на месте (рави не record-hideable), но скрытое поле → null;
        // прочие поля (nameAr, reliabilityGrade) нетронуты.
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", f.hadithId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "nameAr"), contains("البخاري")))
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "reliabilityGrade"), contains("THIQA")))
                .andExpect(jsonPath(nodeJsonpath(f.collectorId(), "gradeText"), contains(nullValue())));
    }
}
