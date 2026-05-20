package ru.basnukaev.argumentmap.qa.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
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
            "id, title, body, status, asked_by, accepted_answer_id, created_at, updated_at";

    private static final RowMapper<Question> ROW_MAPPER = (rs, rn) -> new Question(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("body"),
            QuestionStatus.valueOf(rs.getString("status")),
            rs.getObject("asked_by", UUID.class),
            rs.getObject("accepted_answer_id", UUID.class),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public QuestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Question save(Question q) {
        // accepted_answer_id опускаем - на создании вопроса ответа ещё нет
        // (FK на answers.id, которая бы нарушала integrity). Колонка имеет
        // DEFAULT NULL в схеме - устанавливается через acceptAnswer
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, body, status, asked_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
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

    /**
     * Установить принятый ответ + status = ANSWERED атомарно. Этап 19.c,
     * ADR-034. Возвращает {@code true} если строка обновлена.
     */
    public boolean setAcceptedAnswer(UUID questionId, UUID answerId) {
        return jdbcTemplate.update(
                "UPDATE questions SET accepted_answer_id = ?, status = 'ANSWERED', "
                        + "updated_at = now() WHERE id = ?",
                answerId, questionId) > 0;
    }

    /**
     * Снять принятие ответа: accepted_answer_id = NULL + status = OPEN.
     * Этап 19.c, ADR-034.
     */
    public boolean revokeAcceptedAnswer(UUID questionId) {
        return jdbcTemplate.update(
                "UPDATE questions SET accepted_answer_id = NULL, status = 'OPEN', "
                        + "updated_at = now() WHERE id = ?",
                questionId) > 0;
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
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, query);
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    /**
     * Пагинированный аналог {@link #findAll(QuestionStatus, String)}.
     * Использует partial индекс {@code idx_questions_status_created}.
     */
    public List<Question> findPage(QuestionStatus status, String query,
                                   int limit, int offset) {
        return findPage(status, query, limit, offset, null);
    }

    /**
     * Vision 49d Section 2.1: sort overload для popularity ranking.
     * sort: "recent" (default), "popular" (answer_count DESC),
     * "alphabetical" (title ASC). Computed answer_count через subquery
     * - дороже чем denormalized counter, но в Phase 1 без миграции
     * counters.
     */
    public List<Question> findPage(QuestionStatus status, String query,
                                   int limit, int offset, String sort) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(COLUMNS).append(" FROM questions WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, query);
        sql.append(orderByForSort(sort)).append(" LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    /** Whitelist ORDER BY clause для sort - SQL safety. */
    private static String orderByForSort(String sort) {
        if (sort == null) return " ORDER BY created_at DESC";
        return switch (sort) {
            case "popular" -> " ORDER BY (SELECT COUNT(*) FROM answers a WHERE a.question_id = questions.id) DESC, created_at DESC";
            case "alphabetical" -> " ORDER BY title ASC";
            case "recent" -> " ORDER BY created_at DESC";
            default -> " ORDER BY created_at DESC";
        };
    }

    public long countFiltered(QuestionStatus status, String query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM questions WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, query);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private static void appendFilters(StringBuilder sql, List<Object> args,
                                      QuestionStatus status, String query) {
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND title ILIKE ?");
            args.add("%" + query + "%");
        }
    }

    /**
     * Partial update - title/body/status. Updates {@code updated_at = now()}
     * автоматически. {@code null} значения = no change в соответствующем поле.
     *
     * @return {@code true} если row обновлён (question найден)
     */
    public boolean update(UUID id, String title, String body, QuestionStatus status) {
        StringBuilder sql = new StringBuilder("UPDATE questions SET updated_at = now()");
        List<Object> args = new ArrayList<>();
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
