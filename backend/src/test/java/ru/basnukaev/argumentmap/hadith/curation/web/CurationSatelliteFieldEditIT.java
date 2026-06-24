package ru.basnukaev.argumentmap.hadith.curation.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.repository.HadithExplanationRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRulingRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorCommentaryRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;

/**
 * IT field-level edit + field-hide для сателлитов хадиса/рави (Фаза 5, §5):
 * правка поля видна в EFFECTIVE detail; поле-уровневое скрытие → null; matn/
 * sanad record-hide вырезан гостю / раскрыт ADMIN; первоисточник
 * (text_ar / commentary comments) отвергается whitelist'ом (400).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CurationSatelliteFieldEditIT {

    private static final String OVERRIDES = "/api/v1/admin/curation/overrides";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private HadithRulingRepository rulingRepository;
    @Autowired private HadithExplanationRepository explanationRepository;
    @Autowired private MatnRepository matnRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private NarratorCommentaryRepository commentaryRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("admin", UserRole.ADMIN);
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@t.com", role);
        return id;
    }

    private UUID newHadith() {
        UUID hid = UUID.randomUUID();
        hadithRepository.save(new Hadith(hid, null, 1, "متن", HadithStatus.CANONICAL, null, null, Instant.now()));
        return hid;
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

    private void hideRecord(String table, UUID entityId) throws Exception {
        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"" + table + "\",\"entityId\":\"" + entityId
                                + "\",\"fieldName\":\"__record__\",\"hidden\":true,\"reason\":\"модерация\"}"))
                .andExpect(status().isOk());
    }

    // ── hd_rulings: field edit + field-hide ─────────────────────────────────────

    @Test
    void editRulingField_effectiveInDetail() throws Exception {
        UUID hid = newHadith();
        UUID rid = UUID.randomUUID();
        rulingRepository.save(new HadithRuling(rid, hid, "البخاري", 256, "حسن-импорт",
                "كتاب", 1, 1, "{}", Instant.now()));

        edit("hd_rulings", rid, "ruling_text", "GRADE-EDIT");

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulings.length()").value(1))
                .andExpect(jsonPath("$.rulings[0].rulingText").value("GRADE-EDIT"));
    }

    @Test
    void fieldHideRulerName_nullInDetail_recordStillVisible() throws Exception {
        UUID hid = newHadith();
        UUID rid = UUID.randomUUID();
        rulingRepository.save(new HadithRuling(rid, hid, "одиозный", 300, "صحيح",
                "كتاب", 1, 1, "{}", Instant.now()));

        hideField("hd_rulings", rid, "ruler_name");

        // запись видна гостю (это поле-, не запись-уровень), но ruler_name занулён
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulings.length()").value(1))
                .andExpect(jsonPath("$.rulings[0].rulerName").value(nullValue()))
                .andExpect(jsonPath("$.rulings[0].rulingText").value("صحيح"));
    }

    // ── hd_explanations: field edit ─────────────────────────────────────────────

    @Test
    void editExplanationField_effectiveInDetail() throws Exception {
        UUID hid = newHadith();
        UUID eid = UUID.randomUUID();
        explanationRepository.save(new HadithExplanation(eid, hid, "SHARH", "كتاب-импорт",
                "автор", 700, 10, 2, "текст шарха", "{}", Instant.now()));

        edit("hd_explanations", eid, "book_name", "BOOK-EDIT");

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanations.length()").value(1))
                .andExpect(jsonPath("$.explanations[0].bookName").value("BOOK-EDIT"));
    }

    // ── hd_matns: meta edit + record-hide + first-source reject ─────────────────

    @Test
    void editMatnMetaField_effectiveInDetail() throws Exception {
        UUID hid = newHadith();
        UUID mid = UUID.randomUUID();
        matnRepository.save(new Matn(mid, hid, "نص-عربي", "نص-منوّن", null, null,
                null, 5, 10, 1, true, "сводка", "{}", Instant.now()));

        edit("hd_matns", mid, "page_no", "42");

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matns.length()").value(1))
                .andExpect(jsonPath("$.matns[0].pageNo").value(42))
                // первоисточник нетронут
                .andExpect(jsonPath("$.matns[0].textAr").value("نص-عربي"));
    }

    @Test
    void hiddenMatn_cutForGuest_revealedForAdmin() throws Exception {
        UUID hid = newHadith();
        UUID hidden = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        // Latin-маркеры в divergence_summary (стабильнее arabic-литералов для
        // JSON-матчера; различают скрытую/видимую без bidi/normalization-флака)
        matnRepository.save(new Matn(hidden, hid, "ar", "norm", null, null,
                null, 1, 1, 1, true, "HIDDEN-VAR", "{}", Instant.now()));
        matnRepository.save(new Matn(visible, hid, "ar", "norm", null, null,
                null, 2, 2, 2, false, "VISIBLE-VAR", "{}", Instant.now()));

        hideRecord("hd_matns", hidden);

        // гость — скрытая вариация вырезана
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matns.length()").value(1))
                .andExpect(jsonPath("$.matns[*].divergenceSummary", contains("VISIBLE-VAR")));

        // ADMIN — обе, скрытая помечена hiddenByAdmin + причина
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid)
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matns.length()").value(2))
                .andExpect(jsonPath("$.matns[?(@.hiddenByAdmin==true)].divergenceSummary",
                        hasItem("HIDDEN-VAR")))
                .andExpect(jsonPath("$.matns[?(@.hiddenByAdmin==true)].hideReason",
                        hasItem("модерация")));
    }

    @Test
    void matnFirstSourceField_returns400() throws Exception {
        UUID hid = newHadith();
        UUID mid = UUID.randomUUID();
        matnRepository.save(new Matn(mid, hid, "نص-عربي", "نص-منوّن", null, null,
                null, 1, 1, 1, true, null, "{}", Instant.now()));

        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"hd_matns\",\"entityId\":\"" + mid
                                + "\",\"fieldName\":\"text_ar\",\"value\":\"подмена\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("curation-field-not-editable")));
    }

    // ── hd_sanads: chain_grade edit + record-hide ───────────────────────────────

    @Test
    void editSanadChainGrade_effectiveInDetail() throws Exception {
        // chain_grade в БД ограничен CHECK'ом (SAHIH/HASAN/DAIF/MAUDU/UNKNOWN);
        // overlay-значение в отдельной таблице CHECK не подчиняется, но кладём
        // валидный enum — правка DAIF → SAHIH.
        UUID hid = newHadith();
        UUID sid = UUID.randomUUID();
        sanadRepository.save(new Sanad(sid, hid, "DAIF", null, null, true, "{}", Instant.now()));

        edit("hd_sanads", sid, "chain_grade", "SAHIH");

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanads.length()").value(1))
                .andExpect(jsonPath("$.sanads[0].chainGrade").value("SAHIH"));
    }

    @Test
    void hiddenSanad_cutForGuest_revealedForAdmin() throws Exception {
        UUID hid = newHadith();
        UUID hidden = UUID.randomUUID();
        UUID visible = UUID.randomUUID();
        sanadRepository.save(new Sanad(hidden, hid, "DAIF", null, null, true, "{}", Instant.now()));
        sanadRepository.save(new Sanad(visible, hid, "SAHIH", null, null, false, "{}", Instant.now()));

        hideRecord("hd_sanads", hidden);

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanads.length()").value(1))
                .andExpect(jsonPath("$.sanads[*].chainGrade", contains("SAHIH")));

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hid)
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanads.length()").value(2))
                .andExpect(jsonPath("$.sanads[?(@.hiddenByAdmin==true)].chainGrade",
                        hasItem("DAIF")))
                .andExpect(jsonPath("$.sanads[?(@.hiddenByAdmin==true)].hideReason",
                        hasItem("модерация")));
    }

    // ── hd_narrator_commentaries: field edit + first-source reject ──────────────

    @Test
    void editCommentaryField_effectiveInDetail() throws Exception {
        UUID nid = UUID.randomUUID();
        narratorRepository.save(new Narrator(nid, null, "راو", "راو", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now()));
        UUID cid = UUID.randomUUID();
        commentaryRepository.save(new NarratorCommentary(cid, nid, "ابن حجر", 852,
                "book-import", "автор", 1, 1, List.of("VERBATIM-QUOTE"), "{}", Instant.now()));

        edit("hd_narrator_commentaries", cid, "book_name", "BOOK-EDIT");

        mockMvc.perform(get("/api/v1/hadith/narrators/{id}", nid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentaries.length()").value(1))
                .andExpect(jsonPath("$.commentaries[0].bookName").value("BOOK-EDIT"))
                // comments verbatim — первоисточник, нетронут
                .andExpect(jsonPath("$.commentaries[0].comments", contains("VERBATIM-QUOTE")));
    }

    @Test
    void commentaryCommentsField_returns400() throws Exception {
        UUID nid = UUID.randomUUID();
        narratorRepository.save(new Narrator(nid, null, "راو", "راو", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now()));
        UUID cid = UUID.randomUUID();
        commentaryRepository.save(new NarratorCommentary(cid, nid, "ابن حجر", 852,
                "كتاب", "автор", 1, 1, List.of("ثقة"), "{}", Instant.now()));

        mockMvc.perform(put(OVERRIDES).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"entityTable\":\"hd_narrator_commentaries\",\"entityId\":\"" + cid
                                + "\",\"fieldName\":\"comments\",\"value\":\"подмена\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("curation-field-not-editable")));
    }
}
