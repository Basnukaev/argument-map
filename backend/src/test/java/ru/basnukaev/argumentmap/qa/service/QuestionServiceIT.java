package ru.basnukaev.argumentmap.qa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.qa.domain.Question;

/**
 * Сервисные IT для {@link QuestionService}, фокус на PATCH-семантике body
 * (баг #6 Tier-3: blank body должен очищаться в NULL, а не в "").
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class QuestionServiceIT {

    @Autowired private QuestionService questionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");
    }

    @Test
    void updateQuestion_emptyBody_clearsToNull_notEmptyString() {
        Question q = questionService.createQuestion("Заголовок", "исходное тело", userId);

        Question updated = questionService.updateQuestion(q.id(), null, "", null);

        assertThat(updated.body()).isNull();
        // самое важное: в БД именно NULL, не пустая строка "" (баг #6)
        String dbBody = jdbcTemplate.queryForObject(
                "SELECT body FROM questions WHERE id = ?", String.class, q.id());
        assertThat(dbBody).isNull();
    }

    @Test
    void updateQuestion_whitespaceBody_clearsToNull() {
        Question q = questionService.createQuestion("Заголовок", "исходное тело", userId);

        Question updated = questionService.updateQuestion(q.id(), null, "   ", null);

        assertThat(updated.body()).isNull();
    }

    @Test
    void updateQuestion_nullBody_leavesBodyUnchanged() {
        Question q = questionService.createQuestion("Заголовок", "сохрани меня", userId);

        Question updated = questionService.updateQuestion(q.id(), "Новый заголовок", null, null);

        assertThat(updated.body()).isEqualTo("сохрани меня");
        assertThat(updated.title()).isEqualTo("Новый заголовок");
    }

    @Test
    void updateQuestion_nonBlankBody_storesTrimmedValue() {
        Question q = questionService.createQuestion("Заголовок", "old", userId);

        Question updated = questionService.updateQuestion(q.id(), null, "  новое тело  ", null);

        assertThat(updated.body()).isEqualTo("новое тело");
    }
}
