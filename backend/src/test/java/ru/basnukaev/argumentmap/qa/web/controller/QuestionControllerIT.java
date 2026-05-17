package ru.basnukaev.argumentmap.qa.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import ru.basnukaev.argumentmap.qa.web.dto.CreateQuestionRequest;
import ru.basnukaev.argumentmap.qa.web.dto.UpdateQuestionRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QuestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    @Test
    void createQuestion_returns201() throws Exception {
        var req = new CreateQuestionRequest("Каково положение?", "Подробности");

        mockMvc.perform(post("/api/v1/questions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Каково положение?"))
                .andExpect(jsonPath("$.body").value("Подробности"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.askedBy").value(userId.toString()));
    }

    @Test
    void createQuestion_trimsTitleAndBody() throws Exception {
        var req = new CreateQuestionRequest("  trim me  ", "  trim body  ");

        mockMvc.perform(post("/api/v1/questions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("trim me"))
                .andExpect(jsonPath("$.body").value("trim body"));
    }

    @Test
    void createQuestion_blankTitle_returns400() throws Exception {
        var req = new CreateQuestionRequest("   ", "body");

        mockMvc.perform(post("/api/v1/questions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestion_missingUserHeader_returns400() throws Exception {
        var req = new CreateQuestionRequest("Title", null);

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listQuestions_filtersByStatus() throws Exception {
        UUID q1 = createDirect("Open one", "OPEN");
        UUID q2 = createDirect("Closed one", "CLOSED");

        mockMvc.perform(get("/api/v1/questions").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(q2.toString()));

        // sanity: без фильтра видно оба (включая возможно созданные прошлыми тестами,
        // но мы в @Transactional rollback - значит только наши)
        mockMvc.perform(get("/api/v1/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
        // suppress unused warning
        assert q1 != null;
    }

    @Test
    void listQuestions_searchByTitleSubstring() throws Exception {
        createDirect("Каково положение хадиса", "OPEN");
        createDirect("Когда читать дуа", "OPEN");

        mockMvc.perform(get("/api/v1/questions").param("q", "хадис"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Каково положение хадиса"));
    }

    @Test
    void listQuestions_paginated_returnsCorrectPage() throws Exception {
        for (int i = 0; i < 5; i++) {
            createDirect("q-" + i, "OPEN");
        }
        mockMvc.perform(get("/api/v1/questions")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrev").value(true));
    }

    @Test
    void getQuestion_whenMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/questions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Вопрос не найден"));
    }

    @Test
    void patchQuestion_updatesStatus() throws Exception {
        UUID qid = createDirect("Title", "OPEN");
        var req = new UpdateQuestionRequest(null, null,
                ru.basnukaev.argumentmap.qa.domain.QuestionStatus.CLOSED);

        // ADR-043 Amendment: автор может update
        mockMvc.perform(patch("/api/v1/questions/" + qid)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.title").value("Title"));
    }

    @Test
    void patchQuestion_blankTitle_returns400() throws Exception {
        UUID qid = createDirect("Title", "OPEN");
        var req = new UpdateQuestionRequest("   ", null, null);

        mockMvc.perform(patch("/api/v1/questions/" + qid)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchQuestion_whenMissing_returns404() throws Exception {
        var req = new UpdateQuestionRequest("New", null, null);

        mockMvc.perform(patch("/api/v1/questions/" + UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchQuestion_byNonAuthor_returns403() throws Exception {
        // ADR-043 Amendment (Этап 22.c): only author or admin
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "user-" + otherUserId, otherUserId + "@example.com"
        );
        UUID qid = createDirect("Title", "OPEN");
        var req = new UpdateQuestionRequest("Хакер", null, null);

        mockMvc.perform(patch("/api/v1/questions/" + qid)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers
                        .containsString("forbidden-question-write")));
    }

    @Test
    void deleteQuestion_returns204() throws Exception {
        UUID qid = createDirect("Title", "OPEN");

        // ADR-043 Amendment: автор может delete
        mockMvc.perform(delete("/api/v1/questions/" + qid)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/questions/" + qid))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteQuestion_whenMissing_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/questions/" + UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteQuestion_byNonAuthor_returns403() throws Exception {
        // ADR-043 Amendment (Этап 22.c): only author or admin
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "user-" + otherUserId, otherUserId + "@example.com"
        );
        UUID qid = createDirect("Title", "OPEN");

        mockMvc.perform(delete("/api/v1/questions/" + qid)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers
                        .containsString("forbidden-question-write")));
    }

    private UUID createDirect(String title, String statusName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, status, asked_by) VALUES (?, ?, ?, ?)",
                id, title, statusName, userId
        );
        return id;
    }
}
