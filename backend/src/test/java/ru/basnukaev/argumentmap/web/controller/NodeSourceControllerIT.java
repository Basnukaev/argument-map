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
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.web.dto.AttachSourceRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeSourceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID sourceId;

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
        sourceId = insertSource();
    }

    @Test
    void attachSource_returns201_andLinkPersisted() throws Exception {
        var req = new AttachSourceRequest(sourceId, "точная цитата", "контекст");

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.sourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.quote").value("точная цитата"));
    }

    @Test
    void attachSource_whenNodeMissing_returns404() throws Exception {
        var req = new AttachSourceRequest(sourceId, null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void attachSource_whenSourceMissing_returns404() throws Exception {
        var req = new AttachSourceRequest(UUID.randomUUID(), null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("source-not-found")));
    }

    @Test
    void listNodeSources_returnsAttachments() throws Exception {
        attach(sourceId, "q1", "c1");
        UUID source2 = insertSource();
        attach(source2, "q2", "c2");

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listNodeSources_whenNodeMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void detachSource_returns204() throws Exception {
        attach(sourceId, null, null);

        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/sources/{sourceId}", nodeId, sourceId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detachSource_whenNotAttached_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/sources/{sourceId}", nodeId, sourceId))
                .andExpect(status().isNotFound());
    }

    private void attach(UUID source, String quote, String context) throws Exception {
        var req = new AttachSourceRequest(source, quote, context);
        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private UUID insertNode() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertSource() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title) VALUES (?, ?, ?)",
                id, SourceType.BOOK.name(), "title-" + id
        );
        return id;
    }
}
