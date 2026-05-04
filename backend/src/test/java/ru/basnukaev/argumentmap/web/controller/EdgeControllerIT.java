package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EdgeControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeA;
    private UUID nodeB;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                topicId, "T", userId
        );
        nodeA = insertNode(topicId);
        nodeB = insertNode(topicId);
    }

    @Test
    void createEdge_returns201() throws Exception {
        var req = new CreateEdgeRequest(nodeA, nodeB, EdgeType.SUPPORTS, "потому что");

        mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fromNodeId").value(nodeA.toString()))
                .andExpect(jsonPath("$.toNodeId").value(nodeB.toString()))
                .andExpect(jsonPath("$.edgeType").value("SUPPORTS"));
    }

    @Test
    void createEdge_selfLoop_returns422() throws Exception {
        var req = new CreateEdgeRequest(nodeA, nodeA, EdgeType.SUPPORTS, null);

        mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(containsString("invalid-edge")))
                .andExpect(jsonPath("$.detail").value(containsString("на себя")));
    }

    @Test
    void createEdge_crossTopic_returns422() throws Exception {
        UUID otherTopic = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                otherTopic, "Other", userId
        );
        UUID foreign = insertNode(otherTopic);
        var req = new CreateEdgeRequest(nodeA, foreign, EdgeType.SUPPORTS, null);

        mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("границу")));
    }

    @Test
    void createEdge_missingFromNode_returns404() throws Exception {
        var req = new CreateEdgeRequest(UUID.randomUUID(), nodeB, EdgeType.SUPPORTS, null);

        mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void createEdge_missingFields_returns400() throws Exception {
        // edgeType отсутствует
        String body = "{\"fromNodeId\":\"" + nodeA + "\",\"toNodeId\":\"" + nodeB + "\"}";

        mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='edgeType')]").exists());
    }

    @Test
    void deleteEdge_existing_returns204() throws Exception {
        var req = new CreateEdgeRequest(nodeA, nodeB, EdgeType.REFUTES, null);
        String json = mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID edgeId = UUID.fromString(objectMapper.readTree(json).get("id").asText());

        mockMvc.perform(delete("/api/v1/edges/{id}", edgeId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteEdge_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/edges/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID insertNode(UUID topic) {
        return insertNodeWithType(topic, NodeType.CLAIM);
    }

    private UUID insertNodeWithType(UUID topic, NodeType nodeType) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, nodeType.name(), "c", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    @Test
    void createEdge_disallowedPair_returns422() throws Exception {
        UUID question = insertNodeWithType(topicId, NodeType.QUESTION);
        var req = new CreateEdgeRequest(question, nodeA, EdgeType.SUPPORTS, null);

        mockMvc.perform(post("/api/v1/edges")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(containsString("invalid-edge")))
                .andExpect(jsonPath("$.detail").value(containsString("недопустим")));
    }
}
