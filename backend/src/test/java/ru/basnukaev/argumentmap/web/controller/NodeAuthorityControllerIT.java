package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Stance;
import ru.basnukaev.argumentmap.web.dto.AttachAuthorityRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeAuthorityControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID authorityId;

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
        nodeId = insertNode();
        authorityId = insertAuthority("Ибн Таймия");
    }

    @Test
    void attachAuthority_returns201_withStance() throws Exception {
        var req = new AttachAuthorityRequest(authorityId, Stance.OPPOSES);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/authorities", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorityId").value(authorityId.toString()))
                .andExpect(jsonPath("$.stance").value("OPPOSES"));
    }

    @Test
    void attachAuthority_missingStance_returns400() throws Exception {
        String body = "{\"authorityId\":\"" + authorityId + "\"}";

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/authorities", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='stance')]").exists());
    }

    @Test
    void attachAuthority_whenNodeMissing_returns404() throws Exception {
        var req = new AttachAuthorityRequest(authorityId, Stance.HOLDS);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/authorities", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void attachAuthority_whenAuthorityMissing_returns404() throws Exception {
        var req = new AttachAuthorityRequest(UUID.randomUUID(), Stance.HOLDS);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/authorities", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("authority-not-found")));
    }

    @Test
    void listNodeAuthorities_returnsAttachments() throws Exception {
        attach(authorityId, Stance.HOLDS);
        UUID a2 = insertAuthority("Ибн Хаджар");
        attach(a2, Stance.OPPOSES);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/authorities", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void detachAuthority_returns204() throws Exception {
        attach(authorityId, Stance.NEUTRAL);

        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/authorities/{authorityId}", nodeId, authorityId))
                .andExpect(status().isNoContent());
    }

    @Test
    void detachAuthority_whenNotAttached_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/authorities/{authorityId}", nodeId, authorityId))
                .andExpect(status().isNotFound());
    }

    private void attach(UUID authority, Stance stance) throws Exception {
        var req = new AttachAuthorityRequest(authority, stance);
        mockMvc.perform(post("/api/v1/nodes/{nodeId}/authorities", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private UUID insertNode() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, weight, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(),
                5, userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertAuthority(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO authorities (id, name) VALUES (?, ?)",
                id, name
        );
        return id;
    }
}
