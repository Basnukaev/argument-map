package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.NodeAuthority;
import ru.basnukaev.argumentmap.domain.Stance;

@Repository
public class NodeAuthorityRepository {

    private static final String COLUMNS = "node_id, authority_id, stance, created_at";

    private static final RowMapper<NodeAuthority> ROW_MAPPER = (rs, rn) -> new NodeAuthority(
            rs.getObject("node_id", UUID.class),
            rs.getObject("authority_id", UUID.class),
            Stance.valueOf(rs.getString("stance")),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public NodeAuthorityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NodeAuthority save(NodeAuthority link) {
        jdbcTemplate.update(
                "INSERT INTO node_authorities (" + COLUMNS + ") VALUES (?, ?, ?, ?)",
                link.nodeId(),
                link.authorityId(),
                link.stance().name(),
                odt(link.createdAt())
        );
        return link;
    }

    public Optional<NodeAuthority> findByIds(UUID nodeId, UUID authorityId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_authorities WHERE node_id = ? AND authority_id = ?",
                ROW_MAPPER,
                nodeId, authorityId
        ).stream().findFirst();
    }

    public List<NodeAuthority> findByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_authorities WHERE node_id = ? ORDER BY created_at",
                ROW_MAPPER,
                nodeId
        );
    }

    public List<NodeAuthority> findByAuthorityId(UUID authorityId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM node_authorities WHERE authority_id = ? ORDER BY created_at",
                ROW_MAPPER,
                authorityId
        );
    }

    public boolean delete(UUID nodeId, UUID authorityId) {
        return jdbcTemplate.update(
                "DELETE FROM node_authorities WHERE node_id = ? AND authority_id = ?",
                nodeId, authorityId
        ) > 0;
    }
}
