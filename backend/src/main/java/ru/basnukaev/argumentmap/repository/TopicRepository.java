package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Topic;

@Repository
public class TopicRepository {

    private static final String COLUMNS =
            "id, title, description, root_node_id, created_by, created_at, visibility, status_algorithm";

    private static final RowMapper<Topic> ROW_MAPPER = (rs, rn) -> new Topic(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("root_node_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at"),
            rs.getString("visibility"),
            rs.getString("status_algorithm")
    );

    /**
     * Один SQL для темы + двух агрегатов через LEFT JOIN-подзапросы. Альтернатива -
     * 2 subquery в SELECT-листе (читается проще, но N+1 при многих темах).
     * COALESCE возвращает 0 если нет nodes/edges (новая пустая тема). Edges
     * аггрегируются через JOIN с nodes чтобы знать topic_id ребра (edges не
     * хранят его напрямую - см. ADR-003 о двух таблицах nodes+edges)
     */
    private static final String COUNTS_SQL_BASE = """
            SELECT t.id, t.title, t.description, t.root_node_id, t.created_by, t.created_at, t.visibility, t.status_algorithm,
                   COALESCE(nc.cnt, 0) AS node_count,
                   COALESCE(ec.cnt, 0) AS edge_count
            FROM topics t
            LEFT JOIN (
                SELECT topic_id, COUNT(*) AS cnt FROM nodes GROUP BY topic_id
            ) nc ON nc.topic_id = t.id
            LEFT JOIN (
                SELECT n.topic_id, COUNT(*) AS cnt
                FROM edges e
                JOIN nodes n ON n.id = e.from_node_id
                GROUP BY n.topic_id
            ) ec ON ec.topic_id = t.id
            """;

    private static final RowMapper<TopicWithCounts> WITH_COUNTS_MAPPER = (rs, rn) -> new TopicWithCounts(
            ROW_MAPPER.mapRow(rs, rn),
            rs.getInt("node_count"),
            rs.getInt("edge_count")
    );

    private final JdbcTemplate jdbcTemplate;

    public TopicRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Topic save(Topic topic) {
        jdbcTemplate.update(
                "INSERT INTO topics (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                topic.id(),
                topic.title(),
                topic.description(),
                topic.rootNodeId(),
                topic.createdBy(),
                odt(topic.createdAt()),
                topic.visibility(),
                topic.statusAlgorithm()
        );
        return topic;
    }

    public Optional<Topic> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topics WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Topic> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM topics ORDER BY created_at", ROW_MAPPER);
    }

    public List<TopicWithCounts> findAllWithCounts() {
        return jdbcTemplate.query(
                COUNTS_SQL_BASE + " ORDER BY t.created_at",
                WITH_COUNTS_MAPPER
        );
    }

    public Optional<TopicWithCounts> findByIdWithCounts(UUID id) {
        return jdbcTemplate.query(
                COUNTS_SQL_BASE + " WHERE t.id = ?",
                WITH_COUNTS_MAPPER,
                id
        ).stream().findFirst();
    }

    public void updateRootNodeId(UUID topicId, UUID rootNodeId) {
        jdbcTemplate.update("UPDATE topics SET root_node_id = ? WHERE id = ?", rootNodeId, topicId);
    }

    /**
     * Меняет visibility темы (ADR-043). Проверка прав - в Service.
     */
    public void updateVisibility(UUID topicId, String visibility) {
        jdbcTemplate.update("UPDATE topics SET visibility = ? WHERE id = ?", visibility, topicId);
    }

    /**
     * Меняет title + description темы. Оба передаются всегда (после
     * merge'а с текущими значениями в Service). Permission/валидация - в Service.
     */
    public void updateTitleAndDescription(UUID topicId, String title, String description) {
        jdbcTemplate.update(
                "UPDATE topics SET title = ?, description = ? WHERE id = ?",
                title, description, topicId
        );
    }

    /**
     * Меняет алгоритм пересчёта статусов (ADR-044). Проверка прав и
     * валидация значения - в Service.
     */
    public void updateStatusAlgorithm(UUID topicId, String algorithm) {
        jdbcTemplate.update("UPDATE topics SET status_algorithm = ? WHERE id = ?", algorithm, topicId);
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM topics WHERE id = ?", id) > 0;
    }

    /**
     * Темы, видимые пользователю (ADR-043) - UNION трёх веток:
     * <ul>
     *   <li>PRIVATE темы где он owner (created_by = userId)
     *   <li>SHARED темы где он owner ИЛИ член (через JOIN topic_members)
     *   <li>все PUBLIC темы
     * </ul>
     * DISTINCT через UNION (без ALL) - убирает дубли если user одновременно
     * owner и member своей же SHARED темы (member self-add edge case).
     * ADMIN - используй {@link #findAllWithCounts()} напрямую через PermissionService.
     */
    public List<TopicWithCounts> findVisibleToUserWithCounts(UUID userId) {
        String sql = """
                SELECT t.id, t.title, t.description, t.root_node_id, t.created_by, t.created_at, t.visibility, t.status_algorithm,
                       COALESCE(nc.cnt, 0) AS node_count,
                       COALESCE(ec.cnt, 0) AS edge_count
                FROM topics t
                LEFT JOIN (
                    SELECT topic_id, COUNT(*) AS cnt FROM nodes GROUP BY topic_id
                ) nc ON nc.topic_id = t.id
                LEFT JOIN (
                    SELECT n.topic_id, COUNT(*) AS cnt
                    FROM edges e
                    JOIN nodes n ON n.id = e.from_node_id
                    GROUP BY n.topic_id
                ) ec ON ec.topic_id = t.id
                WHERE t.visibility = 'PUBLIC'
                   OR t.created_by = ?
                   OR (t.visibility = 'SHARED' AND EXISTS (
                        SELECT 1 FROM topic_members tm
                        WHERE tm.topic_id = t.id AND tm.user_id = ?
                   ))
                ORDER BY created_at
                """;
        return jdbcTemplate.query(sql, WITH_COUNTS_MAPPER, userId, userId);
    }

    private static final String VISIBLE_TO_USER_WHERE = """
            WHERE (t.visibility = 'PUBLIC'
                   OR t.created_by = ?
                   OR (t.visibility = 'SHARED' AND EXISTS (
                        SELECT 1 FROM topic_members tm
                        WHERE tm.topic_id = t.id AND tm.user_id = ?
                   )))
            """;

    /**
     * Пагинированный список тем видимых user'у с опциональным фильтром
     * по visibility (внутри set'а уже видимых). Используется REST endpoint
     * GET /api/v1/topics с {@code ?page=&size=&visibility=}.
     *
     * <p>Порядок ORDER BY t.created_at DESC - последние созданные сверху.
     * Это отличается от {@link #findVisibleToUserWithCounts(UUID)}
     * (ASC), но для UI list page последние созданные - что нужно
     * по UX-default'у (consistent with sources/questions).
     */
    public List<TopicWithCounts> findVisibleToUserPage(UUID userId, String visibility,
                                                       int limit, int offset) {
        StringBuilder sql = new StringBuilder(COUNTS_SQL_BASE).append(VISIBLE_TO_USER_WHERE);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(userId);
        if (visibility != null) {
            sql.append(" AND t.visibility = ?");
            args.add(visibility);
        }
        sql.append(" ORDER BY t.created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), WITH_COUNTS_MAPPER, args.toArray());
    }

    public long countVisibleToUser(UUID userId, String visibility) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM topics t ")
                .append(VISIBLE_TO_USER_WHERE);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(userId);
        if (visibility != null) {
            sql.append(" AND t.visibility = ?");
            args.add(visibility);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /**
     * ADMIN paginated: все темы, без visibility-фильтра пользователя.
     * Используется когда role=ADMIN на REST уровне.
     */
    public List<TopicWithCounts> findAllPage(String visibility, int limit, int offset) {
        StringBuilder sql = new StringBuilder(COUNTS_SQL_BASE).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (visibility != null) {
            sql.append(" AND t.visibility = ?");
            args.add(visibility);
        }
        sql.append(" ORDER BY t.created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), WITH_COUNTS_MAPPER, args.toArray());
    }

    public long countAll(String visibility) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM topics t WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (visibility != null) {
            sql.append(" AND t.visibility = ?");
            args.add(visibility);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }
}
