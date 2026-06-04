package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;

@Repository
public class NarratorRelationRepository {

    private static final String COLUMNS =
            "id, narrator_id, related_narrator_id, related_name, role, cnt, created_at";

    private static final RowMapper<NarratorRelation> ROW_MAPPER = (rs, rn) -> new NarratorRelation(
            rs.getObject("id", UUID.class),
            rs.getObject("narrator_id", UUID.class),
            rs.getObject("related_narrator_id", UUID.class),
            rs.getString("related_name"),
            rs.getString("role"),
            (Integer) rs.getObject("cnt"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public NarratorRelationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NarratorRelation save(NarratorRelation r) {
        jdbcTemplate.update(
                "INSERT INTO hd_narrator_relations (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.narratorId(), r.relatedNarratorId(), r.relatedName(),
                r.role(), r.cnt(), odt(r.createdAt()));
        return r;
    }

    public List<NarratorRelation> findByNarratorId(UUID narratorId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrator_relations WHERE narrator_id = ? "
                        + "ORDER BY role, cnt DESC NULLS LAST, created_at ASC",
                ROW_MAPPER, narratorId);
    }

    public void deleteByNarratorId(UUID narratorId) {
        jdbcTemplate.update("DELETE FROM hd_narrator_relations WHERE narrator_id = ?", narratorId);
    }

    /**
     * Выборка связей с незаполненным {@code related_narrator_id} — вход
     * Java-резолва (план 3, решение 11б). Порядок по {@code created_at, id}
     * для детерминированного постраничного обхода.
     */
    public List<NarratorRelation> findUnresolved(int limit, long offset) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_narrator_relations "
                        + "WHERE related_narrator_id IS NULL "
                        + "ORDER BY created_at, id "
                        + "LIMIT ? OFFSET ?",
                ROW_MAPPER, limit, offset);
    }

    /**
     * Проставляет {@code related_narrator_id} у одной связи по id —
     * вызывается Java-резолвом только при ровно одном кандидате по
     * нормализованному имени (гомонимы → связь остаётся NULL, known limitation).
     */
    public void updateRelatedNarratorId(UUID relationId, UUID narratorId) {
        jdbcTemplate.update(
                "UPDATE hd_narrator_relations SET related_narrator_id = ? WHERE id = ?",
                narratorId, relationId);
    }
}
