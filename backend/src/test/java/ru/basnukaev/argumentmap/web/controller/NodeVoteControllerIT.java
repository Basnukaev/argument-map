package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.service.NodeService;
import ru.basnukaev.argumentmap.web.dto.CreateNodeVoteRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeVoteControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NodeService nodeService;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PUBLIC')",
                topicId, "T", userId
        );
        nodeId = nodeService.createNode(topicId, NodeType.ARGUMENT, "Аргумент", userId).id();
    }

    @Test
    void POST_vote_upvote_returns201_andStats() throws Exception {
        var req = new CreateNodeVoteRequest(1);

        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.downvotes").value(0))
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void POST_vote_downvote_returns201() throws Exception {
        var req = new CreateNodeVoteRequest(-1);

        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.downvotes").value(1))
                .andExpect(jsonPath("$.score").value(-1))
                .andExpect(jsonPath("$.userVote").value(-1));
    }

    @Test
    void POST_vote_invalidWeight_returns400() throws Exception {
        var req = new CreateNodeVoteRequest(2);

        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-vote")));
    }

    @Test
    void POST_vote_missingWeight_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_vote_nodeMissing_returns404() throws Exception {
        var req = new CreateNodeVoteRequest(1);

        mockMvc.perform(post("/api/v1/nodes/{id}/vote", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void DELETE_vote_existing_returns204() throws Exception {
        // подготовка - сначала vote через POST
        var req = new CreateNodeVoteRequest(1);
        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        // голоса больше нет - GET stats показывает чистое состояние
        mockMvc.perform(get("/api/v1/nodes/{id}/votes", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.userVote").doesNotExist());
    }

    @Test
    void DELETE_vote_notVoted_returns204_idempotent() throws Exception {
        mockMvc.perform(delete("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void GET_votes_returnsStats() throws Exception {
        var req = new CreateNodeVoteRequest(-1);
        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/nodes/{id}/votes", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.downvotes").value(1))
                .andExpect(jsonPath("$.score").value(-1))
                .andExpect(jsonPath("$.userVote").value(-1));
    }

    @Test
    void GET_graph_includesVoteFields_inNodeResponse() throws Exception {
        // vote перед запросом графа - должны увидеть актуальную статистику
        var req = new CreateNodeVoteRequest(1);
        mockMvc.perform(post("/api/v1/nodes/{id}/vote", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/topics/{id}/graph", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id=='" + nodeId + "')].voteUpvotes").value(1))
                .andExpect(jsonPath("$.nodes[?(@.id=='" + nodeId + "')].voteScore").value(1))
                .andExpect(jsonPath("$.nodes[?(@.id=='" + nodeId + "')].userVote").value(1));
    }
}
