package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;

/** DAO {@code am_staging_narrator} (миграция 72). План 2 alminasa. */
@Repository
public class AmNarratorStagingDao {

    private static final RowMapper<AmNarratorRow> ROW_MAPPER = (rs, rn) -> new AmNarratorRow(
            rs.getLong("narrator_id"),
            rs.getString("full_name"),
            rs.getString("grade"),
            rs.getString("level"),
            rs.getString("raw_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public AmNarratorStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmNarratorRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_narrator (
                    narrator_id, full_name, grade, level, raw
                )
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (narrator_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    grade = EXCLUDED.grade,
                    level = EXCLUDED.level,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.narratorId(), r.fullName(), r.grade(), r.level(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    /**
     * Keyset-пагинация по PK {@code narrator_id}.
     * При {@code afterId == null} возвращает строки с начала.
     */
    public List<AmNarratorRow> findPage(Long afterId, int limit) {
        if (afterId == null) {
            return jdbcTemplate.query(
                    "SELECT narrator_id, full_name, grade, level, raw::text AS raw_json "
                            + "FROM am_staging_narrator "
                            + "ORDER BY narrator_id "
                            + "LIMIT ?",
                    ROW_MAPPER, limit);
        }
        return jdbcTemplate.query(
                "SELECT narrator_id, full_name, grade, level, raw::text AS raw_json "
                        + "FROM am_staging_narrator "
                        + "WHERE narrator_id > ? "
                        + "ORDER BY narrator_id "
                        + "LIMIT ?",
                ROW_MAPPER, afterId, limit);
    }

    /** Поиск по PK {@code narrator_id}. */
    public Optional<AmNarratorRow> findById(long narratorId) {
        return jdbcTemplate.query(
                "SELECT narrator_id, full_name, grade, level, raw::text AS raw_json "
                        + "FROM am_staging_narrator WHERE narrator_id = ?",
                ROW_MAPPER, narratorId
        ).stream().findFirst();
    }

    /** Все уже-скраулённые id — seed дедупликации краулера при resume. */
    public List<Long> findAllIds() {
        return jdbcTemplate.queryForList(
                "SELECT narrator_id FROM am_staging_narrator", Long.class);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_narrator", Integer.class);
        return count == null ? 0 : count;
    }
}
