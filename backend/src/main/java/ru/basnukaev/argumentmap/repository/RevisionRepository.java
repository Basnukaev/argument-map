package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Revision;

@Repository
public class RevisionRepository {

    private static final String COLUMNS =
            "id, node_id, content_before, content_after, changed_by, changed_at";

    private static final RowMapper<Revision> ROW_MAPPER = (rs, rn) -> new Revision(
            rs.getObject("id", UUID.class),
            rs.getObject("node_id", UUID.class),
            rs.getString("content_before"),
            rs.getString("content_after"),
            rs.getObject("changed_by", UUID.class),
            instant(rs, "changed_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public RevisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Revision save(Revision revision) {
        jdbcTemplate.update(
                "INSERT INTO revisions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                revision.id(),
                revision.nodeId(),
                revision.contentBefore(),
                revision.contentAfter(),
                revision.changedBy(),
                odt(revision.changedAt())
        );
        return revision;
    }

    public Optional<Revision> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM revisions WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Revision> findByNodeId(UUID nodeId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM revisions WHERE node_id = ? ORDER BY changed_at",
                ROW_MAPPER,
                nodeId
        );
    }
}
