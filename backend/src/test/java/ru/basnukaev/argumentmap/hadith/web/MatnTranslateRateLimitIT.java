package ru.basnukaev.argumentmap.hadith.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.context.TestPropertySource;
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
 * IT для cost-guard'а AI-перевода (P2-3). Лимит понижен до 2 запросов в
 * окно через {@link TestPropertySource}. Стаб {@link LlmClient} (@Primary)
 * вместо реальных HTTP-вызовов. Покрывает:
 * <ul>
 *   <li>под лимитом → 200 (первый перевод LLM-bound);</li>
 *   <li>над лимитом → 429 ProblemDetail (type too-many-requests + Retry-After);</li>
 *   <li>cached re-request НЕ тратит бюджет (только LLM-bound запросы считаются);</li>
 *   <li>ADMIN освобождён от лимита.</li>
 * </ul>
 *
 * <p>Каждый тест работает на свежесозданном пользователе (random UUID) —
 * singleton-бин лимитера накапливает state per-user, изоляция тестов
 * достигается уникальностью userId, не reset'ом.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "hadith.translate.rate-limit.enabled=true",
        "hadith.translate.rate-limit.requests-per-window=2",
        "hadith.translate.rate-limit.window=PT1H"
})
@Transactional
class MatnTranslateRateLimitIT {

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

    @BeforeEach
    void setUp() {
        StubLlmConfig.CALLS.set(0);
    }

    @Test
    void underLimitThenOverLimit_returns200then429() throws Exception {
        UUID user = insertUser("rl-user", UserRole.USER);
        UUID m1 = insertMatn();
        UUID m2 = insertMatn();
        UUID m3 = insertMatn();

        // лимит=2: первые два первых-перевода (LLM-bound) проходят
        translate(user, m1).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
        translate(user, m2).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(2);

        // третий LLM-bound запрос превышает лимит → 429 ProblemDetail
        translate(user, m3)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type", Matchers.containsString("too-many-requests")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        // отклонённый запрос НЕ дошёл до LLM
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(2);
    }

    @Test
    void cachedRequestsDoNotConsumeBudget() throws Exception {
        UUID user = insertUser("rl-cache-user", UserRole.USER);
        UUID m1 = insertMatn();
        UUID m2 = insertMatn();

        // один LLM-bound запрос — бюджет = 1/2
        translate(user, m1).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(1);

        // повторные запросы на УЖЕ переведённый матн → cached=true, бюджет НЕ тратят.
        // Если бы тратили — после двух таких ещё один LLM-bound упёрся бы в 429.
        for (int i = 0; i < 5; i++) {
            translate(user, m1).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cached").value(true));
        }
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(1);

        // второй первый-перевод всё ещё проходит (бюджет был 1/2, не съеден кэшем)
        translate(user, m2).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(2);
    }

    @Test
    void adminIsExemptFromLimit() throws Exception {
        UUID admin = insertUser("rl-admin", UserRole.ADMIN);

        // намного больше лимита (2) первых-переводов разными матнами — ADMIN не блокируется
        for (int i = 0; i < 5; i++) {
            UUID m = insertMatn();
            translate(admin, m).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cached").value(false));
        }
        assertThat(StubLlmConfig.CALLS.get()).isEqualTo(5);
    }

    private org.springframework.test.web.servlet.ResultActions translate(UUID userId, UUID matnId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/hadith/matns/{matnId}/translate", matnId)
                .header("X-User-Id", userId.toString())
                .contentType("application/json")
                .content("{\"lang\":\"ru\"}"));
    }

    private UUID insertMatn() {
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
        return matn.id();
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, suffix + "-" + id, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
