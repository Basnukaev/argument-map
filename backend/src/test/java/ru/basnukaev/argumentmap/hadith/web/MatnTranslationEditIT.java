package ru.basnukaev.argumentmap.hadith.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;

/**
 * IT для ручной правки сохранённого перевода матна (C9, ADMIN-only).
 * Эндпоинт {@code PATCH /api/v1/hadith/matns/{matnId}/translation} БЕЗ
 * LLM-вызова — стаб {@link ru.basnukaev.argumentmap.ai.LlmClient} не нужен
 * (в этом тесте перевод никогда не генерируется). Покрывает: аноним → 401;
 * не-ADMIN (USER/SCHOLAR) → 403; ADMIN валидным текстом → 200 + реальная
 * запись в text_ru, вторая колонка text_en не затронута; matn-not-found →
 * 404; blank text → 400 (от @Valid @NotBlank).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MatnTranslationEditIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private MatnRepository matnRepository;

    private UUID matnId;
    private UUID userId;
    private UUID scholarId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        Hadith hadith = new Hadith(
                UUID.randomUUID(), null, 1,
                "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now
        );
        hadithRepository.save(hadith);

        Matn matn = new Matn(
                UUID.randomUUID(), hadith.id(),
                "إنما الأعمال بالنيات", "innama al-amal",
                null, null,
                null, 1, null, null, true, null, null, now
        );
        matnRepository.save(matn);
        matnId = matn.id();

        userId = insertUser("edit-user", UserRole.USER);
        scholarId = insertUser("edit-scholar", UserRole.SCHOLAR);
        adminId = insertUser("edit-admin", UserRole.ADMIN);
    }

    @Test
    void PATCH_edit_noUser_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\",\"text\":\"Правка\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", Matchers.containsString("invalid-token")));
    }

    @Test
    void PATCH_edit_nonAdminUser_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\",\"text\":\"Правка\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", Matchers.containsString("forbidden-admin-only")));
    }

    @Test
    void PATCH_edit_nonAdminScholar_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .header("X-User-Id", scholarId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\",\"text\":\"Правка\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", Matchers.containsString("forbidden-admin-only")));
    }

    @Test
    void PATCH_edit_admin_overwritesRuColumnOnly() throws Exception {
        // префилл обеих колонок, чтобы убедиться: правка ru НЕ трогает en
        jdbcTemplate.update(
                "UPDATE hd_matns SET text_ru = 'старый ru', text_en = 'kept en' WHERE id = ?",
                matnId);

        String newText = "Поистине, дела по намерениям (правка админа)";
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\",\"text\":\"" + newText + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matnId").value(matnId.toString()))
                .andExpect(jsonPath("$.lang").value("ru"))
                .andExpect(jsonPath("$.text").value(newText))
                // LLM не звался — отдаём сохранённое значение, cached=true
                .andExpect(jsonPath("$.cached").value(true));

        String persistedRu = jdbcTemplate.queryForObject(
                "SELECT text_ru FROM hd_matns WHERE id = ?", String.class, matnId);
        String persistedEn = jdbcTemplate.queryForObject(
                "SELECT text_en FROM hd_matns WHERE id = ?", String.class, matnId);
        assertThat(persistedRu).isEqualTo(newText);
        assertThat(persistedEn).isEqualTo("kept en");
    }

    @Test
    void PATCH_edit_admin_trimsAndPersists() throws Exception {
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"en\",\"text\":\"  Verily deeds  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("en"))
                .andExpect(jsonPath("$.text").value("Verily deeds"));

        String persistedEn = jdbcTemplate.queryForObject(
                "SELECT text_en FROM hd_matns WHERE id = ?", String.class, matnId);
        assertThat(persistedEn).isEqualTo("Verily deeds");
    }

    @Test
    void PATCH_edit_unknownMatn_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", UUID.randomUUID())
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\",\"text\":\"Правка\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", Matchers.containsString("matn-not-found")));
    }

    @Test
    void PATCH_edit_blankText_returns400() throws Exception {
        // @NotBlank ловит пустую строку на уровне @Valid → 400 validation
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\",\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("validation")));
    }

    @Test
    void PATCH_edit_invalidLang_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/hadith/matns/{matnId}/translation", matnId)
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"fr\",\"text\":\"Правка\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("validation")));
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
