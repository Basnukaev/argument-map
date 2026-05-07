package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Topic;

@Repository
public class TopicRepository {

    private static final String COLUMNS = "id, title, description, root_node_id, created_by, created_at";

    private static final RowMapper<Topic> ROW_MAPPER = (rs, rn) -> new Topic(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("root_node_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at")
    );

    /**
     * Один SQL для темы + двух агрегатов через LEFT JOIN-подзапросы. Альтернатива -
     * 2 subquery в SELECT-листе (читается проще, но N+1 при многих темах).
     * COALESCE возвращает 0 если нет nodes/edges (новая пустая тема). Edges
     * аггрегируются через JOIN с nodes чтобы знать topic_id ребра (edges не
     * хранят его напрямую - см. ADR-003 о двух таблицах nodes+edges)
     */
    private static final String COUNTS_SQL_BASE = """
            SELECT t.id, t.title, t.description, t.root_node_id, t.created_by, t.created_at,
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
                "INSERT INTO topics (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                topic.id(),
                topic.title(),
                topic.description(),
                topic.rootNodeId(),
                topic.createdBy(),
                odt(topic.createdAt())
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

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM topics WHERE id = ?", id) > 0;
    }
}
