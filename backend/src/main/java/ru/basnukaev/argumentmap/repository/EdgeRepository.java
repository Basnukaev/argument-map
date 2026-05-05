package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;

@Repository
public class EdgeRepository {

    private static final String COLUMNS =
            "id, from_node_id, to_node_id, edge_type, rationale, source_handle, target_handle, created_by, created_at";

    private static final RowMapper<Edge> ROW_MAPPER = (rs, rn) -> new Edge(
            rs.getObject("id", UUID.class),
            rs.getObject("from_node_id", UUID.class),
            rs.getObject("to_node_id", UUID.class),
            EdgeType.valueOf(rs.getString("edge_type")),
            rs.getString("rationale"),
            rs.getString("source_handle"),
            rs.getString("target_handle"),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public EdgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Edge save(Edge edge) {
        jdbcTemplate.update(
                "INSERT INTO edges (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                edge.id(),
                edge.fromNodeId(),
                edge.toNodeId(),
                edge.edgeType().name(),
                edge.rationale(),
                edge.sourceHandle(),
                edge.targetHandle(),
                edge.createdBy(),
                odt(edge.createdAt())
        );
        return edge;
    }

    public Optional<Edge> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM edges WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Edge> findByFromNodeId(UUID fromNodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM edges WHERE from_node_id = ? ORDER BY created_at",
                ROW_MAPPER,
                fromNodeId
        );
    }

    public List<Edge> findByToNodeId(UUID toNodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM edges WHERE to_node_id = ? ORDER BY created_at",
                ROW_MAPPER,
                toNodeId
        );
    }

    /**
     * Все рёбра, принадлежащие теме. Определяется через from-узел: ребро
     * считается частью темы, если его исходный узел лежит в данной теме.
     * Допускаем, что рёбра не пересекают границы тем — это инвариант,
     * проверяемый в EdgeService при создании.
     */
    public List<Edge> findByTopicId(UUID topicId) {
        return jdbcTemplate.query(
                "SELECT e.id, e.from_node_id, e.to_node_id, e.edge_type, e.rationale, "
                        + "e.source_handle, e.target_handle, e.created_by, e.created_at "
                        + "FROM edges e JOIN nodes n ON n.id = e.from_node_id "
                        + "WHERE n.topic_id = ? ORDER BY e.created_at",
                ROW_MAPPER,
                topicId
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM edges WHERE id = ?", id) > 0;
    }
}
