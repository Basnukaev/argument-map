package ru.basnukaev.argumentmap.qa.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import ru.basnukaev.argumentmap.qa.web.dto.UpdateAnswerRequest;

/**
 * IT для AnswerController с author/admin guard (ADR-043 Amendment, Этап 22.c).
 *
 * <p>POST/GET и accept/revoke - не покрываются здесь (есть в
 * AnswerControllerIT существующих/AnswerCitationServiceIT). Этот файл
 * фокусирован на permission checks для update/delete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AnswerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID authorId;
    private UUID otherUserId;
    private UUID questionId;
    private UUID answerId;

    @BeforeEach
    void setUp() {
        authorId = insertUser("author");
        otherUserId = insertUser("other");

        questionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, status, asked_by) VALUES (?, ?, ?, ?)",
                questionId, "Тестовый вопрос", "OPEN", authorId
        );

        answerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO answers (id, question_id, body, author_id) VALUES (?, ?, ?, ?)",
                answerId, questionId, "Тестовый ответ", authorId
        );
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com", "USER"
        );
        return id;
    }

    private void promoteToAdmin(UUID userId) {
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
    }

    // ---- DELETE permissions ----

    @Test
    void DELETE_answer_byAuthor_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/answers/{aid}", answerId)
                        .header("X-User-Id", authorId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void DELETE_answer_byNonAuthor_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/answers/{aid}", answerId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-answer-write")));
    }

    @Test
    void DELETE_answer_byAdmin_returns204() throws Exception {
        // ADMIN bypass - даже если не автор
        UUID adminId = insertUser("admin");
        promoteToAdmin(adminId);

        // X-User-Id заполняет userId, но role из SecurityContext - которая
        // в dev-фильтре читает из БД (тест-проверка: ADMIN bypass работает)
        mockMvc.perform(delete("/api/v1/answers/{aid}", answerId)
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void DELETE_answer_missingUserHeader_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/answers/{aid}", answerId))
                .andExpect(status().isBadRequest());
    }

    // ---- PATCH permissions ----

    @Test
    void PATCH_answer_byAuthor_returns200() throws Exception {
        var req = new UpdateAnswerRequest("Новое тело");

        mockMvc.perform(patch("/api/v1/answers/{aid}", answerId)
                        .header("X-User-Id", authorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Новое тело"));
    }

    @Test
    void PATCH_answer_byNonAuthor_returns403() throws Exception {
        var req = new UpdateAnswerRequest("Хакерский edit");

        mockMvc.perform(patch("/api/v1/answers/{aid}", answerId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-answer-write")));
    }
}
