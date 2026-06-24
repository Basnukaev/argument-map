package ru.basnukaev.argumentmap.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.service.JwtService;

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
        "actuator.security.password=testpass",
        // P0-3: датасорс из Testcontainers @ServiceConnection (localhost) под
        // prod-profile споткнулся бы о DatasourceConfigValidator. Гард покрыт
        // отдельным DatasourceConfigValidatorTest - здесь отключаем.
        "app.datasource.prod-guard=false"
})
@Transactional
class GuestAccessProdProfileIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private UUID ownerId;
    private UUID publicTopicId;
    private UUID privateTopicId;
    private UUID privateBookPageId;
    private UUID publicBookId;

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

        // PRIVATE книга + её страница: под-ресурсы /pages/{id}/** не должны
        // течь анониму (C-1, ревью С62).
        UUID privateBookId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_books (id, book_type, title, language, created_by, visibility) "
                        + "VALUES (?, 'BOOK', ?, 'ar', ?, 'PRIVATE')",
                privateBookId, "Приватная книга", ownerId);
        privateBookPageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_pages (id, book_id, page_number, text_content) "
                        + "VALUES (?, ?, 1, ?)",
                privateBookPageId, privateBookId, "текст приватной страницы");

        // PUBLIC книга — для проверки публичного инкремента просмотров (С64).
        publicBookId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_books (id, book_type, title, language, created_by, visibility) "
                        + "VALUES (?, 'BOOK', ?, 'ar', ?, 'PUBLIC')",
                publicBookId, "Публичная книга", ownerId);

        // Член ПУБЛИЧНОЙ темы и книги — для проверки P1-4 (ADR-064 follow-up):
        // member-list НЕ должен утекать анониму, даже если тема/книга PUBLIC.
        jdbcTemplate.update(
                "INSERT INTO topic_members (id, topic_id, user_id, role, added_by) "
                        + "VALUES (?, ?, ?, 'MEMBER', ?)",
                UUID.randomUUID(), publicTopicId, ownerId, ownerId);
        jdbcTemplate.update(
                "INSERT INTO lib_book_members (id, book_id, user_id, role, added_by) "
                        + "VALUES (?, ?, ?, 'MEMBER', ?)",
                UUID.randomUUID(), publicBookId, ownerId, ownerId);
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
    void anonymous_getPrivateBookPageRegions_returns403() throws Exception {
        // C-1 (ревью С62): permitAll /library/pages/** не должен открывать
        // анониму метадату регионов (bbox + extractedText) страницы приватной
        // книги. Read-guard в ImageRegionService.
        mockMvc.perform(get("/api/v1/library/pages/{id}/regions", privateBookPageId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-book-access")));
    }

    @Test
    void anonymous_getPrivateBookPageAiEditStatus_returns403() throws Exception {
        // C-1 (ревью С62): и AI-edit-статус страницы приватной книги. Read-guard
        // в AiEditController.getAiEditStatus.
        mockMvc.perform(get("/api/v1/library/pages/{id}/ai-edit", privateBookPageId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-book-access")));
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
    void anonymous_postBookView_returns204_publicCounter() throws Exception {
        // С64: инкремент просмотров — ЕДИНСТВЕННАЯ мутация, открытая анониму в
        // prod (публичный счётчик, контент не раскрывается). Контраст с
        // createTopic → 401 выше. Проверяем, что POST доходит до контроллера
        // (permitMatcher работает) и реально инкрементит счётчик.
        mockMvc.perform(post("/api/v1/library/books/{id}/views", publicBookId))
                .andExpect(status().isNoContent());
        Integer views = jdbcTemplate.queryForObject(
                "SELECT view_count FROM lib_books WHERE id = ?", Integer.class, publicBookId);
        assertThat(views).isEqualTo(1);
    }

    @Test
    void anonymous_getAuthMe_returns401_authNotWeakened() throws Exception {
        // /auth/me всегда authenticated() - guest view его не трогает.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_listPublicTopicMembers_returns401_notLeaked() throws Exception {
        // P1-4 (ADR-064 follow-up): даже у PUBLIC темы member-list вынесен из
        // guest permitAll за authenticated(). Аноним → 401 на Spring-гейте,
        // username/UUID участников не утекают (раньше было 200).
        mockMvc.perform(get("/api/v1/topics/{id}/members", publicTopicId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("unauthorized")));
    }

    @Test
    void authenticated_listPublicTopicMembers_returns200() throws Exception {
        // С Bearer токеном правило authenticated() проходит; дальше per-entity
        // RBAC (assertCanRead) пускает к PUBLIC-теме → список членов виден.
        mockMvc.perform(get("/api/v1/topics/{id}/members", publicTopicId)
                        .header("Authorization", "Bearer " + bearerFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ownerId.toString()));
    }

    @Test
    void anonymous_listPublicBookMembers_returns401_notLeaked() throws Exception {
        // Зеркало topics: member-list книги тоже за authenticated().
        mockMvc.perform(get("/api/v1/library/books/{id}/members", publicBookId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("unauthorized")));
    }

    @Test
    void authenticated_listPublicBookMembers_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/library/books/{id}/members", publicBookId)
                        .header("Authorization", "Bearer " + bearerFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ownerId.toString()));
    }

    /**
     * Bearer access-токен для существующего пользователя. В prod profile нет
     * XUserIdFilter, поэтому authenticated()-кейсы используют реальный JWT.
     */
    private String bearerFor(UUID userId) {
        Instant now = Instant.now();
        User user = new User(userId, "user-" + userId, userId + "@example.com",
                null, "USER", true, now, now);
        return jwtService.generateAccessToken(user);
    }
}
