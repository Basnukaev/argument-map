package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.NodeSource;

@Repository
public class NodeSourceRepository {

    private static final String COLUMNS = "node_id, source_id, quote, context, created_at";

    private static final RowMapper<NodeSource> ROW_MAPPER = (rs, rn) -> new NodeSource(
            rs.getObject("node_id", UUID.class),
            rs.getObject("source_id", UUID.class),
            rs.getString("quote"),
            rs.getString("context"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public NodeSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NodeSource save(NodeSource link) {
        jdbcTemplate.update(
                "INSERT INTO node_sources (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                link.nodeId(),
                link.sourceId(),
                link.quote(),
                link.context(),
                odt(link.createdAt())
        );
        return link;
    }

    public Optional<NodeSource> findByIds(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE node_id = ? AND source_id = ?",
                ROW_MAPPER,
                nodeId, sourceId
        ).stream().findFirst();
    }

    public List<NodeSource> findByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE node_id = ? ORDER BY created_at",
                ROW_MAPPER,
                nodeId
        );
    }

    public List<NodeSource> findBySourceId(UUID sourceId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_sources WHERE source_id = ? ORDER BY created_at",
                ROW_MAPPER,
                sourceId
        );
    }

    public boolean delete(UUID nodeId, UUID sourceId) {
        return jdbcTemplate.update(
                "DELETE FROM node_sources WHERE node_id = ? AND source_id = ?",
                nodeId, sourceId
        ) > 0;
    }
}
