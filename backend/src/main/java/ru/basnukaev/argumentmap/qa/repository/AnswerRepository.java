package ru.basnukaev.argumentmap.qa.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.qa.domain.Answer;

/**
 * JDBC repository для answers (Этап 19.c, ADR-034). Зеркалит структуру
 * {@code QuestionRepository}.
 */
@Repository
public class AnswerRepository {

    private static final String COLUMNS =
            "id, question_id, body, author_id, created_at, updated_at";

    private static final RowMapper<Answer> ROW_MAPPER = (rs, rn) -> new Answer(
            rs.getObject("id", UUID.class),
            rs.getObject("question_id", UUID.class),
            rs.getString("body"),
            rs.getObject("author_id", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public AnswerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Answer save(Answer a) {
        jdbcTemplate.update(
                "INSERT INTO answers (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                a.id(),
                a.questionId(),
                a.body(),
                a.authorId(),
                odt(a.createdAt()),
                odt(a.updatedAt())
        );
        return a;
    }

    public Optional<Answer> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM answers WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Answer> findByQuestionId(UUID questionId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM answers WHERE question_id = ? ORDER BY created_at",
                ROW_MAPPER,
                questionId
        );
    }

    /**
     * Ответы вопроса в порядке: принятый первым, потом остальные по
     * {@code created_at}. Если {@code acceptedAnswerId} равно {@code null},
     * сортировка эквивалентна {@link #findByQuestionId(UUID)}.
     */
    public List<Answer> findByQuestionIdSortedByAccepted(UUID questionId, UUID acceptedAnswerId) {
        if (acceptedAnswerId == null) {
            return findByQuestionId(questionId);
        }
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM answers WHERE question_id = ? "
                        + "ORDER BY (id = ?) DESC, created_at",
                ROW_MAPPER,
                questionId,
                acceptedAnswerId
        );
    }

    /**
     * Partial update тела ответа. Также обновляет {@code updated_at = now()}.
     */
    public boolean update(UUID id, String body) {
        return jdbcTemplate.update(
                "UPDATE answers SET body = ?, updated_at = now() WHERE id = ?",
                body, id) > 0;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM answers WHERE id = ?", id) > 0;
    }
}
