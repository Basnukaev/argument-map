package ru.basnukaev.argumentmap.qa.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.qa.domain.Question;
import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;

@Repository
public class QuestionRepository {

    private static final String COLUMNS =
            "id, title, body, status, asked_by, created_at, updated_at";

    private static final RowMapper<Question> ROW_MAPPER = (rs, rn) -> new Question(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("body"),
            QuestionStatus.valueOf(rs.getString("status")),
            rs.getObject("asked_by", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public QuestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Question save(Question q) {
        jdbcTemplate.update(
                "INSERT INTO questions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                q.id(),
                q.title(),
                q.body(),
                q.status().name(),
                q.askedBy(),
                odt(q.createdAt()),
                odt(q.updatedAt())
        );
        return q;
    }

    public Optional<Question> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM questions WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    /**
     * Список вопросов. Опциональные фильтры: статус (OPEN/ANSWERED/CLOSED)
     * и search-подстрока по title (case-insensitive). Сортировка - сначала
     * самые новые. Использует partial индекс
     * {@code idx_questions_status_created}.
     */
    public List<Question> findAll(QuestionStatus status, String query) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(COLUMNS).append(" FROM questions WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND title ILIKE ?");
            args.add("%" + query + "%");
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    /**
     * Partial update - title/body/status. Updates {@code updated_at = now()}
     * автоматически. {@code null} значения = no change в соответствующем поле.
     *
     * @return {@code true} если row обновлён (question найден)
     */
    public boolean update(UUID id, String title, String body, QuestionStatus status) {
        StringBuilder sql = new StringBuilder("UPDATE questions SET updated_at = now()");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (title != null) {
            sql.append(", title = ?");
            args.add(title);
        }
        if (body != null) {
            sql.append(", body = ?");
            args.add(body);
        }
        if (status != null) {
            sql.append(", status = ?");
            args.add(status.name());
        }
        sql.append(" WHERE id = ?");
        args.add(id);
        return jdbcTemplate.update(sql.toString(), args.toArray()) > 0;
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM questions WHERE id = ?", id) > 0;
    }
}
