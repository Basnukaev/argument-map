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
import ru.basnukaev.argumentmap.web.dto.CreateTopicVoteRequest;

/**
 * IT для {@link TopicVoteController} - REST голосования за темы (ADR-053).
 * Зеркалит удалённый NodeVoteControllerIT но на уровне тем + проверяет что
 * topic list/detail отдают voteScore.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicVoteControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PUBLIC')",
                topicId, "T", userId
        );
    }

    @Test
    void POST_vote_upvote_returns201_andStats() throws Exception {
        var req = new CreateTopicVoteRequest(1);

        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicId").value(topicId.toString()))
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.downvotes").value(0))
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void POST_vote_downvote_returns201() throws Exception {
        var req = new CreateTopicVoteRequest(-1);

        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
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
    void POST_vote_changeVote_upserts() throws Exception {
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isCreated());

        // меняем голос - upsert, не дубль
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(-1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.downvotes").value(1))
                .andExpect(jsonPath("$.score").value(-1));
    }

    @Test
    void POST_vote_invalidWeight_returns400() throws Exception {
        var req = new CreateTopicVoteRequest(2);

        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-vote")));
    }

    @Test
    void POST_vote_missingWeight_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_vote_topicMissing_returns404() throws Exception {
        var req = new CreateTopicVoteRequest(1);

        mockMvc.perform(post("/api/v1/topics/{id}/vote", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("topic-not-found")));
    }

    @Test
    void POST_vote_privateTopicNonOwner_returns403() throws Exception {
        UUID privTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                privTopicId, "T-priv", userId
        );

        mockMvc.perform(post("/api/v1/topics/{id}/vote", privTopicId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void DELETE_vote_existing_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        // голоса больше нет - GET stats показывает чистое состояние
        mockMvc.perform(get("/api/v1/topics/{id}/votes", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.userVote").doesNotExist());
    }

    @Test
    void DELETE_vote_notVoted_returns204_idempotent() throws Exception {
        mockMvc.perform(delete("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void GET_votes_returnsStats_bulkMultipleUsers() throws Exception {
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(-1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/topics/{id}/votes", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(topicId.toString()))
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.downvotes").value(1))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void GET_topicDetail_includesVoteScore() throws Exception {
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topicId.toString()))
                .andExpect(jsonPath("$.voteScore").value(1))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void GET_topicList_includesVoteScore() throws Exception {
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/topics/{id}/vote", topicId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopicVoteRequest(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + topicId + "')].voteScore").value(2))
                .andExpect(jsonPath("$.items[?(@.id=='" + topicId + "')].userVote").value(1));
    }

    @Test
    void GET_topicList_noVotes_defaultsToZero() throws Exception {
        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + topicId + "')].voteScore").value(0));
    }

    @Test
    void GET_topicDetail_noVotes_userVoteNull() throws Exception {
        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteScore").value(0))
                .andExpect(jsonPath("$.userVote").doesNotExist());
    }
}
