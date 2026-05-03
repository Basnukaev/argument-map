package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.web.dto.CreateNodeRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateNodeRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;

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
    }

    @Test
    void createNode_returns201() throws Exception {
        var req = new CreateNodeRequest(topicId, NodeType.CLAIM, "Тезис", 5);

        mockMvc.perform(post("/api/v1/nodes")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.topicId").value(topicId.toString()))
                .andExpect(jsonPath("$.nodeType").value("CLAIM"))
                .andExpect(jsonPath("$.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.weight").value(5));
    }

    @Test
    void createNode_whenTopicMissing_returns404() throws Exception {
        var req = new CreateNodeRequest(UUID.randomUUID(), NodeType.CLAIM, "x", 5);

        mockMvc.perform(post("/api/v1/nodes")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("topic-not-found")));
    }

    @Test
    void createNode_invalidWeight_returns400_validationError() throws Exception {
        var req = new CreateNodeRequest(topicId, NodeType.CLAIM, "x", 99);

        mockMvc.perform(post("/api/v1/nodes")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='weight')]").exists());
    }

    @Test
    void createNode_blankContent_returns400() throws Exception {
        var req = new CreateNodeRequest(topicId, NodeType.CLAIM, "   ", 5);

        mockMvc.perform(post("/api/v1/nodes")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='content')]").exists());
    }

    @Test
    void updateContent_returns200_andWritesRevision() throws Exception {
        UUID nodeId = createNode("старый");
        var req = new UpdateNodeRequest("новый");

        mockMvc.perform(patch("/api/v1/nodes/{id}", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("новый"));

        mockMvc.perform(get("/api/v1/nodes/{id}/revisions", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contentBefore").value("старый"))
                .andExpect(jsonPath("$[0].contentAfter").value("новый"));
    }

    @Test
    void updateContent_whenNodeMissing_returns404() throws Exception {
        var req = new UpdateNodeRequest("x");

        mockMvc.perform(patch("/api/v1/nodes/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void deleteNode_existing_returns204() throws Exception {
        UUID nodeId = createNode("x");

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNode_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/nodes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRevisions_whenNodeMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/nodes/{id}/revisions", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createNode(String content) throws Exception {
        var req = new CreateNodeRequest(topicId, NodeType.CLAIM, content, 5);
        String json = mockMvc.perform(post("/api/v1/nodes")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
