package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
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
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.service.AuditLogService;

/**
 * IT для {@link AuditLogController} (Этап 22.d, ADR-043 Amendment 3).
 * Покрывает permission rules: owner может видеть audit темы, non-owner
 * получает 403, /audit/me возвращает только свои actions, /audit/admin
 * требует ADMIN role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditLogControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private AuditLogService auditLogService;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner", UserRole.USER);
        otherUserId = insertUser("other", UserRole.USER);
        adminId = insertUser("admin", UserRole.ADMIN);
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com", role
        );
        return id;
    }

    private UUID insertTopic(UUID createdBy, String visibility) {
        UUID id = UUID.randomUUID();
        topicRepository.save(new Topic(
                id, "T", null, null, createdBy, Instant.now(), visibility,
                ru.basnukaev.argumentmap.domain.StatusAlgorithm.MVP
        ));
        return id;
    }

    @Test
    void GET_auditTopic_owner_returns200() throws Exception {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        auditLogService.logCreate(AuditEntityType.TOPIC, topicId, null, null,
                ownerId, Map.of("title", "T"));

        mockMvc.perform(get("/api/v1/audit/topics/{id}", topicId)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].entityType").value("TOPIC"))
                .andExpect(jsonPath("$.items[0].action").value("CREATE"));
    }

    @Test
    void GET_auditTopic_nonOwnerPrivate_returns403() throws Exception {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);

        mockMvc.perform(get("/api/v1/audit/topics/{id}", topicId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void GET_auditTopic_includesChildEntities() throws Exception {
        UUID topicId = insertTopic(ownerId, TopicVisibility.PRIVATE);
        UUID nodeId = UUID.randomUUID();
        UUID edgeId = UUID.randomUUID();

        auditLogService.logCreate(AuditEntityType.TOPIC, topicId, null, null,
                ownerId, Map.of("title", "T"));
        auditLogService.logCreate(AuditEntityType.NODE, nodeId,
                AuditEntityType.TOPIC, topicId, ownerId, Map.of("content", "C"));
        auditLogService.logCreate(AuditEntityType.EDGE, edgeId,
                AuditEntityType.TOPIC, topicId, ownerId, Map.of("rationale", "R"));

        mockMvc.perform(get("/api/v1/audit/topics/{id}", topicId)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void GET_auditMe_returnsOnlyMyActions() throws Exception {
        // Один action ownerId, один - otherUserId
        auditLogService.logCreate(AuditEntityType.TOPIC, UUID.randomUUID(),
                null, null, ownerId, Map.of("v", 1));
        auditLogService.logCreate(AuditEntityType.TOPIC, UUID.randomUUID(),
                null, null, otherUserId, Map.of("v", 2));

        mockMvc.perform(get("/api/v1/audit/me")
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actorUserId").value(ownerId.toString()));
    }

    @Test
    void GET_auditAdmin_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/audit/admin")
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-admin-only")));
    }

    @Test
    void GET_auditAdmin_admin_returns200_withFilters() throws Exception {
        auditLogService.logCreate(AuditEntityType.TOPIC, UUID.randomUUID(),
                null, null, ownerId, Map.of("v", 1));
        auditLogService.logCreate(AuditEntityType.BOOK, UUID.randomUUID(),
                null, null, otherUserId, Map.of("v", 2));

        // без фильтров - оба
        mockMvc.perform(get("/api/v1/audit/admin")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        // фильтр по entityType=BOOK - только один
        mockMvc.perform(get("/api/v1/audit/admin?entityType=BOOK")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].entityType").value("BOOK"));

        // фильтр по actorId=ownerId - только один
        mockMvc.perform(get("/api/v1/audit/admin?actorId=" + ownerId)
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actorUserId").value(ownerId.toString()));
    }

    @Test
    void GET_auditAdmin_invalidEntityType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/audit/admin?entityType=NOT_A_REAL_TYPE")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isBadRequest());
    }
}
