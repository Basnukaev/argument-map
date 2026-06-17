package ru.basnukaev.argumentmap.auth.web.security;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * Guest view (roadmap 49.G / Vision 49d Section 2.5) - анонимный публичный
 * read-only доступ. Прогон под {@code @ActiveProfiles("prod")} ОБЯЗАТЕЛЕН:
 * в dev/test есть транзитный {@code /api/** permitAll} (ADR-040), который
 * замаскировал бы реальную prod-картину. В prod этой ветки нет, поэтому
 * тест бьёт ровно по новому правилу {@code SecurityConfig} - read-only GET
 * публичного контента открыт, мутации и приватное - закрыты.
 *
 * <p>Покрывает контракт безопасности:
 * <ul>
 *   <li>аноним GET PUBLIC темы / read-list'ы → 200 (контент доступен);
 *   <li>аноним GET PRIVATE темы → 403 (RBAC visibility-фильтр не раскрывает
 *       приватное даже под permitAll - guard в service-слое);
 *   <li>аноним POST (мутация) → 401 (resolver бросает invalid-token);
 *   <li>аноним GET {@code /auth/me} → 401 (auth не ослаблен).
 * </ul>
 *
 * <p>prod profile fail-fast: non-placeholder JWT secret + actuator creds
 * (как в {@link ActuatorSecurityProdProfileIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "auth.jwt.secret=test-prod-secret-32chars-or-more-for-hs256-validation",
        "actuator.security.username=testactuator",
        "actuator.security.password=testpass"
})
@Transactional
class GuestAccessProdProfileIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID publicTopicId;
    private UUID privateTopicId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com");

        publicTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PUBLIC')",
                publicTopicId, "Публичная тема", ownerId);

        privateTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                privateTopicId, "Приватная тема", ownerId);
    }

    @Test
    void anonymous_getPublicTopic_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/topics/{id}", publicTopicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicTopicId.toString()))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
    }

    @Test
    void anonymous_getPublicTopicGraph_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/topics/{id}/graph", publicTopicId))
                .andExpect(status().isOk());
    }

    @Test
    void anonymous_getPrivateTopic_returns403_notLeaked() throws Exception {
        // Ключевой security-кейс: permitAll снимает Spring-гейт, но RBAC
        // visibility-фильтр (service-слой, userId=null) приватное не раскрывает.
        mockMvc.perform(get("/api/v1/topics/{id}", privateTopicId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void anonymous_listTopics_returns200_onlyPublic() throws Exception {
        // Список фильтруется до PUBLIC: приватная тема ownerId анониму не видна.
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.visibility == 'PRIVATE')]").isEmpty());
    }

    @Test
    void anonymous_readLists_arePubliclyAccessible() throws Exception {
        // permitAll GET достигает контроллера в prod (иначе 401/403 на гейте).
        mockMvc.perform(get("/api/v1/hadith/hadiths")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hadith/narrators")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/library/books")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/questions")).andExpect(status().isOk());
    }

    @Test
    void anonymous_createTopic_returns401() throws Exception {
        // Мутация не попадает под GET-permitAll. В prod (без dev /api/**
        // permitAll и без XUserIdFilter) аноним блокируется на Spring-уровне
        // → JwtAuthenticationEntryPoint отдаёт 401 unauthorized (в dev до
        // контроллера дошёл бы и resolver бросил invalid-token; здесь раньше).
        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"rootQuestion\":\"Q?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("unauthorized")));
    }

    @Test
    void anonymous_getAuthMe_returns401_authNotWeakened() throws Exception {
        // /auth/me всегда authenticated() - guest view его не трогает.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
