package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;

/** DAO {@code am_staging_ruling} (миграция 72). План 2 alminasa. */
@Repository
public class AmRulingStagingDao {

    private static final RowMapper<AmRulingRow> ROW_MAPPER = (rs, rn) -> new AmRulingRow(
            rs.getString("es_id"),
            rs.getString("hadith_id"),
            rs.getString("ruler"),
            (Integer) rs.getObject("ruler_dod"),
            rs.getString("narrations_type"),
            rs.getString("raw_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public AmRulingStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmRulingRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_ruling (
                    es_id, hadith_id, ruler, ruler_dod, narrations_type, raw
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (es_id) DO UPDATE SET
                    hadith_id = EXCLUDED.hadith_id,
                    ruler = EXCLUDED.ruler,
                    ruler_dod = EXCLUDED.ruler_dod,
                    narrations_type = EXCLUDED.narrations_type,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.esId(), r.hadithId(), r.ruler(), r.rulerDod(), r.narrationsType(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    /**
     * Все рулинги для данного хадиса — используется маппером (план 3)
     * для union с embedded-рулингами из raw хадис-дока.
     * Индекс {@code idx_am_staging_ruling_hadith} покрывает запрос.
     */
    public List<AmRulingRow> findByHadithId(String hadithId) {
        return jdbcTemplate.query(
                "SELECT es_id, hadith_id, ruler, ruler_dod, narrations_type, "
                        + "raw::text AS raw_json "
                        + "FROM am_staging_ruling WHERE hadith_id = ?",
                ROW_MAPPER, hadithId);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_ruling", Integer.class);
        return count == null ? 0 : count;
    }
}
