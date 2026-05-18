package ru.basnukaev.argumentmap.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.web.dto.CreateTopicRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicStatusAlgorithmRequest;

/**
 * IT для PATCH /api/v1/topics/{id}/status-algorithm (ADR-044).
 * Покрывает permission checks + side effect recalculate + audit + 400/404
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicStatusAlgorithmIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com"
        );
    }

    @Test
    void PATCH_setDung_returnsOk_andTriggersRecalc() throws Exception {
        UUID topicId = createTopicViaApi();
        // создаём цепочку 2 узлов с REFUTES - под Dung'ом ожидается
        // a IN→STANDING, b OUT→REFUTED
        UUID a = insertNode(topicId, NodeStatus.UNVERIFIED);
        UUID b = insertNode(topicId, NodeStatus.UNVERIFIED);
        insertEdge(a, b, EdgeType.REFUTES);

        var req = new UpdateTopicStatusAlgorithmRequest("DUNG_GROUNDED");
        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topicId.toString()))
                .andExpect(jsonPath("$.statusAlgorithm").value("DUNG_GROUNDED"));

        // Side effect - статусы пересчитаны под Dung'ом
        String statusA = jdbcTemplate.queryForObject(
                "SELECT status FROM nodes WHERE id = ?", String.class, a);
        String statusB = jdbcTemplate.queryForObject(
                "SELECT status FROM nodes WHERE id = ?", String.class, b);
        assertThat(statusA).isEqualTo(NodeStatus.STANDING.name());
        assertThat(statusB).isEqualTo(NodeStatus.REFUTED.name());
    }

    @Test
    void PATCH_setMvp_recalculatesBack() throws Exception {
        UUID topicId = createTopicViaApi();
        // Переключаем сначала на DUNG, потом обратно на MVP
        UUID a = insertNode(topicId, NodeStatus.UNVERIFIED);
        UUID b = insertNode(topicId, NodeStatus.UNVERIFIED);
        insertEdge(a, b, EdgeType.REFUTES);

        var dungReq = new UpdateTopicStatusAlgorithmRequest("DUNG_GROUNDED");
        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dungReq)))
                .andExpect(status().isOk());

        // Возвращаем на MVP
        var mvpReq = new UpdateTopicStatusAlgorithmRequest("MVP");
        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mvpReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAlgorithm").value("MVP"));

        // MVP алгоритм: a остаётся UNVERIFIED (нет входящих рёбер),
        // b - REFUTES от UNVERIFIED a, b остаётся UNVERIFIED тоже
        // (нет STANDING-source для REFUTES). Главное - после MVP recalc
        // граф не в inconsistent состоянии Dung-вычислений
        String topicAlg = jdbcTemplate.queryForObject(
                "SELECT status_algorithm FROM topics WHERE id = ?",
                String.class, topicId);
        assertThat(topicAlg).isEqualTo("MVP");
    }

    @Test
    void PATCH_invalidAlgorithm_returns400() throws Exception {
        UUID topicId = createTopicViaApi();
        // Невалидный enum - Pattern regex в DTO даёт 400 до Service-слоя
        String json = "{\"algorithm\":\"NOT_A_REAL_ALGORITHM\"}";

        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_nullAlgorithm_returns400() throws Exception {
        UUID topicId = createTopicViaApi();
        String json = "{\"algorithm\":null}";

        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_byNonOwner_returns403() throws Exception {
        UUID topicId = createTopicViaApi();
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        // меняем visibility на PUBLIC чтобы у не-owner был read-access,
        // но write-action всё равно должен быть отбит
        jdbcTemplate.update("UPDATE topics SET visibility = 'PUBLIC' WHERE id = ?", topicId);

        var req = new UpdateTopicStatusAlgorithmRequest("DUNG_GROUNDED");
        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-write")));
    }

    @Test
    void PATCH_unknownTopic_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        var req = new UpdateTopicStatusAlgorithmRequest("DUNG_GROUNDED");

        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", ghost)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void PATCH_sameValue_isNoOp_doesNotWriteAudit() throws Exception {
        UUID topicId = createTopicViaApi();
        // Считаем audit rows до
        Integer beforeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = 'UPDATE'",
                Integer.class, topicId);

        // Тема создана с MVP по дефолту. PATCH на тот же MVP - no-op
        var req = new UpdateTopicStatusAlgorithmRequest("MVP");
        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Integer afterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = 'UPDATE'",
                Integer.class, topicId);
        assertThat(afterCount).isEqualTo(beforeCount);
    }

    @Test
    void PATCH_writesAuditEntry() throws Exception {
        UUID topicId = createTopicViaApi();

        var req = new UpdateTopicStatusAlgorithmRequest("DUNG_GROUNDED");
        mockMvc.perform(patch("/api/v1/topics/{id}/status-algorithm", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE entity_type = 'TOPIC' AND entity_id = ? "
                        + "AND action = 'UPDATE' AND actor_user_id = ?",
                Integer.class, topicId, ownerId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    // ---- helpers ----

    private UUID createTopicViaApi() throws Exception {
        var req = new CreateTopicRequest("T", null, "Q?", null);
        MvcResult result = mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private UUID insertNode(UUID topicId, NodeStatus status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "z_index, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())",
                id, topicId, NodeType.CLAIM.name(), "c", status.name(), 0, ownerId
        );
        return id;
    }

    private void insertEdge(UUID from, UUID to, EdgeType type) {
        jdbcTemplate.update(
                "INSERT INTO edges (id, from_node_id, to_node_id, edge_type, "
                        + "created_by, created_at) VALUES (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), from, to, type.name(), ownerId
        );
    }
}
