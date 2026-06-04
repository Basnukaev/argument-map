package ru.basnukaev.argumentmap.hadith.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * IT кейса «LLM не настроен» → 503 (План 7, решение 4). БЕЗ стаба
 * LlmClient: дефолтный AnthropicLlmClient с sentinel-ключом
 * ({@code ANTHROPIC_API_KEY:disabled} в test-профиле) → isEnabled()==false
 * → pre-flight guard сервиса бросает MatnTranslationNotConfiguredException.
 * Отдельный класс от {@link HadithTranslationControllerIT}, чтобы стаб
 * @Primary не перекрыл реальный disabled-клиент.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithTranslationNotConfiguredIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private MatnRepository matnRepository;

    private UUID matnId;
    private UUID userId;

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

        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                userId, "noai-" + userId, userId + "@test.com", UserRole.USER);
    }

    @Test
    void POST_translate_llmDisabled_returns503() throws Exception {
        mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content("{\"lang\":\"ru\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", Matchers.containsString("llm-not-configured")))
                .andExpect(jsonPath("$.title").value("AI-перевод не настроен"));
    }
}
