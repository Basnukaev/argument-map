package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;

/** DAO {@code am_staging_narrator} (миграция 72). План 2 alminasa. */
@Repository
public class AmNarratorStagingDao {

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
