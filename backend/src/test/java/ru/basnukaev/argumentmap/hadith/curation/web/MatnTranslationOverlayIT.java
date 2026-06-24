package ru.basnukaev.argumentmap.hadith.curation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;

/**
 * Headline-proof Фазы 6 (ADR-065 §9/§10): перевод primary-матна, отредактированный
 * через C9-эндпоинт {@code PATCH /matns/{id}/translation}, живёт в overlay под
 * СТАБИЛЬНЫМ hadith-keyed ключом ({@code primary_text_ru/en}) и потому переживает
 * delete-recreate реимпорта alminasa (новый matn.id, NULL-колонка перевода).
 *
 * <p>Сценарий: PATCH ru/en → проверяем что записан overlay (не колонка) → GET
 * detail primary-матн показывает перевод → симулируем реимпорт (удаляем матн,
 * создаём новый с другим id и NULL-переводом) → GET detail СНОВА показывает тот
 * же перевод (overlay не зависел от matn.id).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MatnTranslationOverlayIT {

    private static final String DETAIL = "/api/v1/hadith/hadiths/{id}/detail";
    private static final String TRANSLATION = "/api/v1/hadith/matns/{matnId}/translation";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private MatnRepository matnRepository;

    private UUID hadithId;
    private UUID matnId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        Hadith hadith = new Hadith(
                UUID.randomUUID(), null, 1, "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now);
        hadithRepository.save(hadith);
        hadithId = hadith.id();

        matnId = saveMatn(true, "إنما الأعمال بالنيات");

        adminId = insertAdmin();
    }

    @Test
    void primaryTranslation_editedViaC9_survivesReimport() throws Exception {
        // 1. Правка перевода через C9-эндпоинт (ru + en)
        patchTranslation("ru", "Поистине, дела (оцениваются) по намерениям");
        patchTranslation("en", "Verily, deeds are by intentions");

        // 2. Записано в OVERLAY (hadith-keyed), не в колонку матна
        assertThat(overrideValue(hadithId, "primary_text_ru"))
                .isEqualTo("Поистине, дела (оцениваются) по намерениям");
        String columnRu = jdbcTemplate.queryForObject(
                "SELECT text_ru FROM hd_matns WHERE id = ?", String.class, matnId);
        assertThat(columnRu).isNull();

        // 3. GET detail — primary-матн показывает перевод (наложен на чтении)
        mockMvc.perform(get(DETAIL, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matns[?(@.isPrimary==true)].textRu")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Поистине, дела (оцениваются) по намерениям")))
                .andExpect(jsonPath("$.matns[?(@.isPrimary==true)].textEn")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Verily, deeds are by intentions")));

        // 4. Симулируем реимпорт: delete-recreate primary-матна (новый id, NULL-перевод)
        matnRepository.deleteByHadithId(hadithId);
        UUID newMatnId = saveMatn(true, "إنما الأعمال بالنيات");
        assertThat(newMatnId).isNotEqualTo(matnId);

        // 5. ГОЛОВНОЕ ДОКАЗАТЕЛЬСТВО: перевод ВСЁ ЕЩЁ показывается — overlay
        // ключевался hadith_id, не пересоздаваемым matn.id
        mockMvc.perform(get(DETAIL, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matns[?(@.isPrimary==true)].textRu")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Поистине, дела (оцениваются) по намерениям")))
                .andExpect(jsonPath("$.matns[?(@.isPrimary==true)].textEn")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Verily, deeds are by intentions")));
    }

    @Test
    void nonPrimaryTranslation_keyedByMatnId_visibleInDetail() throws Exception {
        // не-primary матн: перевод ключуется matn.id (per-variation путь Фазы 5)
        UUID variantId = saveMatn(false, "نص-вариация");

        patchTranslationFor(variantId, "ru", "перевод вариации");

        // ключ — matn.id (text_ru), не hadith-keyed
        assertThat(overrideValue(variantId, "text_ru")).isEqualTo("перевод вариации");

        mockMvc.perform(get(DETAIL, hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matns[?(@.isPrimary==false)].textRu")
                        .value(org.hamcrest.Matchers.hasItem("перевод вариации")))
                // primary-матн вариативный перевод не получил
                .andExpect(jsonPath("$.matns[?(@.isPrimary==true)].textRu")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.nullValue())));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID saveMatn(boolean primary, String textAr) {
        UUID id = UUID.randomUUID();
        matnRepository.save(new Matn(
                id, hadithId, textAr, textAr, null, null,
                null, primary ? 1 : 2, null, null, primary, null, null, Instant.now()));
        return id;
    }

    private void patchTranslation(String lang, String text) throws Exception {
        patchTranslationFor(matnId, lang, text);
    }

    private void patchTranslationFor(UUID mid, String lang, String text) throws Exception {
        mockMvc.perform(patch(TRANSLATION, mid)
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"" + lang + "\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk());
    }

    private String overrideValue(UUID entityId, String field) {
        List<String> rows = jdbcTemplate.query(
                "SELECT override_value FROM hd_field_overrides WHERE entity_table = 'hd_matns' "
                        + "AND entity_id = ? AND field_name = ?",
                (rs, rn) -> rs.getString("override_value"), entityId, field);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'ADMIN')",
                id, "admin-" + id, id + "@t.com");
        return id;
    }
}
