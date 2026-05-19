package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.web.dto.CreateEdgeRequest;

/**
 * IT для edge z-order endpoints. Mirror NodeZIndexIT.
 * - bringToFront ставит z_index = max(z_index рёбер темы) + 1
 * - sendToBack ставит z_index = min(z_index рёбер темы) - 1
 * - permission check работает: чужой пользователь получает 403
 * - несуществующий edge → 404
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EdgeZIndexIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID topicId;
    private UUID nodeA;
    private UUID nodeB;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, ?)",
                topicId, "Z-Order Edge Topic", ownerId, "PRIVATE"
        );
        nodeA = insertNode(topicId);
        nodeB = insertNode(topicId);
    }

    @Test
    void bringToFront_setsToMaxPlus1() throws Exception {
        // два ребра с z_index=0 по DDL default
        UUID e1 = createEdge(nodeA, nodeB, EdgeType.SUPPORTS);
        UUID nodeC = insertNode(topicId);
        UUID e2 = createEdge(nodeA, nodeC, EdgeType.REFUTES);

        // bringToFront e1: max(0,0)+1 = 1
        mockMvc.perform(post("/api/v1/edges/{id}/z-order/bring-to-front", e1)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e1.toString()))
                .andExpect(jsonPath("$.zIndex").value(1));

        // bringToFront e2: max(1,0)+1 = 2
        mockMvc.perform(post("/api/v1/edges/{id}/z-order/bring-to-front", e2)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e2.toString()))
                .andExpect(jsonPath("$.zIndex").value(2));
    }

    @Test
    void sendToBack_setsToMinMinus1() throws Exception {
        UUID e1 = createEdge(nodeA, nodeB, EdgeType.SUPPORTS);
        UUID nodeC = insertNode(topicId);
        UUID e2 = createEdge(nodeA, nodeC, EdgeType.REFUTES);

        // sendToBack e1: min(0,0)-1 = -1
        mockMvc.perform(post("/api/v1/edges/{id}/z-order/send-to-back", e1)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zIndex").value(-1));

        // sendToBack e2: min(-1,0)-1 = -2
        mockMvc.perform(post("/api/v1/edges/{id}/z-order/send-to-back", e2)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zIndex").value(-2));
    }

    @Test
    void bringToFront_nonOwner_returns403() throws Exception {
        UUID edgeId = createEdge(nodeA, nodeB, EdgeType.SUPPORTS);

        UUID strangerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                strangerId, "stranger-" + strangerId, strangerId + "@example.com"
        );

        mockMvc.perform(post("/api/v1/edges/{id}/z-order/bring-to-front", edgeId)
                        .header("X-User-Id", strangerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void sendToBack_nonOwner_returns403() throws Exception {
        UUID edgeId = createEdge(nodeA, nodeB, EdgeType.SUPPORTS);

        UUID strangerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                strangerId, "stranger-" + strangerId, strangerId + "@example.com"
        );

        mockMvc.perform(post("/api/v1/edges/{id}/z-order/send-to-back", edgeId)
                        .header("X-User-Id", strangerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void bringToFront_nonExistentEdge_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/edges/{id}/z-order/bring-to-front", UUID.randomUUID())
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("edge-not-found")));
    }

    @Test
    void sendToBack_nonExistentEdge_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/edges/{id}/z-order/send-to-back", UUID.randomUUID())
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("edge-not-found")));
    }

    private UUID insertNode(UUID topic) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(),
                ownerId, odt(now), odt(now)
        );
        return id;
    }

    private UUID createEdge(UUID from, UUID to, EdgeType type) throws Exception {
        var req = new CreateEdgeRequest(from, to, type, null, null, null);
        String json = mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
