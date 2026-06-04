package ru.basnukaev.argumentmap.hadith.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;

/**
 * IT для AI-перевода матна (План 7, ADR-058). Стаб {@link LlmClient}
 * (@Primary, фиксированный ответ + счётчик вызовов) — без реальных HTTP
 * вызовов. Покрывает: happy 200 + персист text_ru + cached=false;
 * повторный → cached=true без нового вызова стаба; force без ADMIN → 403;
 * force ADMIN → новый вызов стаба; 401 без юзера; 404 чужой UUID; 400
 * невалидный lang; 422 матн с NULL text_ar. Кейс 503 (LLM disabled) —
 * отдельный класс {@link HadithTranslationNotConfiguredIT} без стаба.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithTranslationControllerIT {

    static final String STUB_TRANSLATION = "[STUB] перевод матна";

    @TestConfiguration
    static class StubLlmConfig {
        static final AtomicInteger CALLS = new AtomicInteger(0);

        @Bean
        @Primary
        LlmClient stubLlmClient() {
            return new LlmClient() {
                @Override
                public boolean isEnabled() {
                    return true;
                }

                @Override
                public String complete(String systemPrompt, String userPrompt) {
                    CALLS.incrementAndGet();
                    return STUB_TRANSLATION;
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private MatnRepository matnRepository;

    private UUID matnId;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        StubLlmConfig.CALLS.set(0);

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

        userId = insertUser("translate-user", UserRole.USER);
        adminId = insertUser("translate-admin", UserRole.ADMIN);
    }

    @Test
    void POST_translate_happyPath_persistsAndReturnsFresh() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matnId").value(matnId.toString()))
                .andExpect(jsonPath("$.lang").value("ru"))
                .andExpect(jsonPath("$.text").value(STUB_TRANSLATION))
                .andExpect(jsonPath("$.cached").value(false));

        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(1);

        String persisted = jdbcTemplate.queryForObject(
                "SELECT text_ru FROM hd_matns WHERE id = ?", String.class, matnId);
        assertThat(persisted).isEqualTo(STUB_TRANSLATION);
    }

    @Test
    void POST_translate_secondCall_returnsCachedWithoutLlm() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(STUB_TRANSLATION))
                .andExpect(jsonPath("$.cached").value(true));

        // счётчик не вырос — повторный перевод взят из БД, без LLM
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(1);
    }

    @Test
    void POST_translate_forceNonAdmin_returns403() throws Exception {
        // сначала наполняем кэш, чтобы force имел смысл
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate?force=true", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", Matchers.containsString("forbidden-admin-only")));
    }

    @Test
    void POST_translate_forceAdmin_regeneratesViaLlm() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isOk());
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate?force=true", matnId)
                        .header("X-User-Id", adminId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));

        // force ADMIN → стаб вызван повторно
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(2);
    }

    @Test
    void POST_translate_noUser_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", Matchers.containsString("invalid-token")));
    }

    @Test
    void POST_translate_unknownMatn_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", Matchers.containsString("matn-not-found")));
    }

    @Test
    void POST_translate_invalidLang_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", Matchers.containsString("validation")));
    }

    @Test
    void POST_translate_blankTextAr_returns422() throws Exception {
        // Матн с пустым text_ar — вставляем напрямую, минуя save(). DB-колонка
        // text_ar NOT NULL, поэтому используем blank-строку (пробел), которую
        // ловит isBlank()-guard в сервисе. 422 ДО LLM-вызова.
        UUID emptyMatnId = UUID.randomUUID();
        UUID hadithId = jdbcTemplate.queryForObject(
                "SELECT hadith_id FROM hd_matns WHERE id = ?", UUID.class, matnId);
        jdbcTemplate.update(
                "INSERT INTO hd_matns (id, hadith_id, text_ar, text_ar_normalized, "
                        + "is_primary, created_at) VALUES (?, ?, ' ', ' ', false, now())",
                emptyMatnId, hadithId);

        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", emptyMatnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", Matchers.containsString("invalid-matn-text")));

        assertThat(StubLlmConfig.CALLS.get()).isZero();
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
