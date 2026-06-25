package ru.basnukaev.argumentmap.hadith.curation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.hamcrest.Matchers;
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
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;

/**
 * IT generic write-API курации (ADR-065 §6). Покрывает RBAC (аноним 401,
 * не-ADMIN 403), защиту первоисточника (400), enum-валидацию, обязательность
 * reason для hide, round-trip правки в EFFECTIVE findById, откат (DELETE).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CurationOverrideControllerIT {

    private static final String URL = "/api/v1/admin/curation/overrides";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private NarratorRepository narratorRepository;

    private UUID hadithId;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        Hadith h = new Hadith(UUID.randomUUID(), null, 1, "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, Instant.now());
        hadithRepository.save(h);
        hadithId = h.id();
        userId = insertUser("user", UserRole.USER);
        adminId = insertUser("admin", UserRole.ADMIN);
    }

    private String body(String fieldName, String json) {
        return "{\"entityTable\":\"hd_hadiths\",\"entityId\":\"" + hadithId
                + "\",\"fieldName\":\"" + fieldName + "\"," + json + "}";
    }

    @Test
    void PUT_anonymous_returns401() throws Exception {
        mockMvc.perform(put(URL).contentType("application/json")
                        .content(body("authenticity", "\"value\":\"SAHIH\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", Matchers.containsString("invalid-token")));
    }

    @Test
    void PUT_nonAdmin_returns403() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(body("authenticity", "\"value\":\"SAHIH\"")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", Matchers.containsString("forbidden-admin-only")));
    }

    @Test
    void PUT_admin_editsAuthenticity_effectiveInFindById() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content(body("authenticity", "\"value\":\"SAHIH\",\"reason\":\"фикс\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldName").value("authenticity"))
                .andExpect(jsonPath("$.value").value("SAHIH"))
                .andExpect(jsonPath("$.editedBy").value(adminId.toString()));

        assertThat(hadithRepository.findById(hadithId).orElseThrow().authenticity())
                .isEqualTo("SAHIH");
    }

    @Test
    void PUT_admin_firstSourceField_returns400_fieldNotEditable() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content(body("full_text_ar", "\"value\":\"подмена\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-field-not-editable")));
    }

    @Test
    void PUT_admin_invalidEnumValue_returns400() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content(body("authenticity", "\"value\":\"BOGUS\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-invalid-enum-value")));
    }

    @Test
    void PUT_admin_hiddenWithoutReason_returns400() throws Exception {
        // hd_hadiths не поддерживает hide вообще → field-not-editable раньше reason-required;
        // берём hideable сателлит-кейс на рулинге невозможно (нет записи) — проверим reason-required
        // на скрываемом поле рави. Создаём рави.
        UUID nid = UUID.randomUUID();
        narratorRepository.save(new Narrator(nid, null, "اسم", "اسم", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now()));
        String narratorBody = "{\"entityTable\":\"hd_narrators\",\"entityId\":\"" + nid
                + "\",\"fieldName\":\"grade_text\",\"hidden\":true}";
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json").content(narratorBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-reason-required")));
    }

    @Test
    void PUT_admin_emptyOverride_returns400() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content(body("authenticity", "\"reason\":\"нет полезной нагрузки\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-empty-override")));
    }

    @Test
    void PUT_admin_entityNotFound_returns404() throws Exception {
        String missing = "{\"entityTable\":\"hd_hadiths\",\"entityId\":\"" + UUID.randomUUID()
                + "\",\"fieldName\":\"authenticity\",\"value\":\"SAHIH\"}";
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json").content(missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-entity-not-found")));
    }

    @Test
    void PUT_invalidEntityTable_returns400() throws Exception {
        String bad = "{\"entityTable\":\"lib_books\",\"entityId\":\"" + UUID.randomUUID()
                + "\",\"fieldName\":\"title\",\"value\":\"x\"}";
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json").content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-invalid-entity-table")));
    }

    @Test
    void PUT_syntheticPrimaryTranslationKey_returns400() throws Exception {
        // primary_text_ru/en пишутся ТОЛЬКО через C9 (по hadith_id-ключу);
        // generic-эндпоинт с ними → 400 (иначе мёртвый override по matn.id)
        String synthetic = "{\"entityTable\":\"hd_matns\",\"entityId\":\"" + UUID.randomUUID()
                + "\",\"fieldName\":\"primary_text_ru\",\"value\":\"перевод\"}";
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json").content(synthetic))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-field-not-editable")));
    }

    @Test
    void PUT_syntheticTransmissionPhraseKey_returns400() throws Exception {
        // transmission_phrase@{position} пишется ТОЛЬКО через выделенный
        // SanadTransmissionPhraseService (по стабильному hadith_id+position);
        // generic-эндпоинт с ним → 400 (иначе мёртвый override по sanad_id)
        String synthetic = "{\"entityTable\":\"hd_sanad_narrators\",\"entityId\":\"" + UUID.randomUUID()
                + "\",\"fieldName\":\"transmission_phrase@2\",\"value\":\"أخبرنا\"}";
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                        .contentType("application/json").content(synthetic))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-field-not-editable")));
    }

    @Test
    void GET_list_returnsOverridesOfEntity() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                .contentType("application/json")
                .content(body("status", "\"value\":\"VARIANT\"")))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("X-User-Id", adminId.toString())
                        .param("entityTable", "hd_hadiths")
                        .param("entityId", hadithId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fieldName").value("status"))
                .andExpect(jsonPath("$[0].value").value("VARIANT"));
    }

    @Test
    void DELETE_existing_revertsToImport_thenMissing404() throws Exception {
        mockMvc.perform(put(URL).header("X-User-Id", adminId.toString())
                .contentType("application/json")
                .content(body("authenticity", "\"value\":\"SAHIH\"")))
                .andExpect(status().isOk());
        assertThat(hadithRepository.findById(hadithId).orElseThrow().authenticity()).isEqualTo("SAHIH");

        mockMvc.perform(delete(URL).header("X-User-Id", adminId.toString())
                        .param("entityTable", "hd_hadiths")
                        .param("entityId", hadithId.toString())
                        .param("fieldName", "authenticity"))
                .andExpect(status().isNoContent());

        // откат: импортное значение (null) восстановлено
        assertThat(hadithRepository.findById(hadithId).orElseThrow().authenticity()).isNull();

        // повторный DELETE → 404
        mockMvc.perform(delete(URL).header("X-User-Id", adminId.toString())
                        .param("entityTable", "hd_hadiths")
                        .param("entityId", hadithId.toString())
                        .param("fieldName", "authenticity"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", Matchers.containsString("curation-override-not-found")));
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
