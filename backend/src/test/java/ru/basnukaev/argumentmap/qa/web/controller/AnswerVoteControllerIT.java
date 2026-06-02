package ru.basnukaev.argumentmap.qa.web.controller;

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
import ru.basnukaev.argumentmap.qa.web.dto.CreateAnswerVoteRequest;

/**
 * IT для {@link AnswerVoteController} - REST голосования за отдельные ответы
 * Q&amp;A. Зеркалит {@code QuestionVoteControllerIT} но на уровне ответов -
 * answers это open discussion (любой authenticated user голосует) + проверяет
 * что answers list отдаёт voteScore.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AnswerVoteControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;
    private UUID questionId;
    private UUID answerId;

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
        questionId = createQuestion("Q", userId);
        answerId = createAnswer(questionId, "A", userId);
    }

    @Test
    void POST_vote_upvote_returns201_andStats() throws Exception {
        var req = new CreateAnswerVoteRequest(1);

        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answerId").value(answerId.toString()))
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.downvotes").value(0))
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void POST_vote_downvote_returns201() throws Exception {
        var req = new CreateAnswerVoteRequest(-1);

        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
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
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isCreated());

        // меняем голос - upsert, не дубль
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(-1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.downvotes").value(1))
                .andExpect(jsonPath("$.score").value(-1));
    }

    @Test
    void POST_vote_byNonAuthor_returns201() throws Exception {
        // open discussion - не-автор тоже может голосовать (никакого 403)
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void POST_vote_invalidWeight_returns400() throws Exception {
        var req = new CreateAnswerVoteRequest(2);

        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-vote")));
    }

    @Test
    void POST_vote_missingWeight_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_vote_answerMissing_returns404() throws Exception {
        var req = new CreateAnswerVoteRequest(1);

        mockMvc.perform(post("/api/v1/answers/{id}/vote", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("answer-not-found")));
    }

    @Test
    void POST_vote_anonymous_returns401() throws Exception {
        // голосование требует authenticated user - без X-User-Id 401
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void DELETE_vote_existing_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        // голоса больше нет - GET vote показывает чистое состояние
        mockMvc.perform(get("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(jsonPath("$.upvotes").value(0))
                .andExpect(jsonPath("$.userVote").doesNotExist());
    }

    @Test
    void DELETE_vote_notVoted_returns204_idempotent() throws Exception {
        mockMvc.perform(delete("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void GET_vote_returnsStats_bulkMultipleUsers() throws Exception {
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(-1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerId").value(answerId.toString()))
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.downvotes").value(1))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.userVote").value(1));
    }

    @Test
    void GET_vote_answerMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/answers/{id}/vote", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("answer-not-found")));
    }

    @Test
    void GET_answerList_includesVoteScore() throws Exception {
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/answers/{id}/vote", answerId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAnswerVoteRequest(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/questions/{qid}/answers", questionId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + answerId + "')].voteScore").value(2))
                .andExpect(jsonPath("$[?(@.id=='" + answerId + "')].userVote").value(1));
    }

    @Test
    void GET_answerList_noVotes_defaultsToZero() throws Exception {
        mockMvc.perform(get("/api/v1/questions/{qid}/answers", questionId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + answerId + "')].voteScore").value(0));
    }

    private UUID createQuestion(String title, UUID askedBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, status, asked_by) VALUES (?, ?, 'OPEN', ?)",
                id, title, askedBy
        );
        return id;
    }

    private UUID createAnswer(UUID qId, String body, UUID authorId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO answers (id, question_id, body, author_id) VALUES (?, ?, ?, ?)",
                id, qId, body, authorId
        );
        return id;
    }
}
