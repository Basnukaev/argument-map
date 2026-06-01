package ru.basnukaev.argumentmap.hadith.sunnah.repository;

import static ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;

/**
 * DAO staging-таблицы {@code sn_staging_hadith}. Phase 5 ETL шаг 2.
 * jsonb-колонки {@code grades} и {@code raw} через {@code ?::jsonb}-cast.
 */
@Repository
public class SunnahHadithDao {

    private static final String COLUMNS =
            "collection_name, hadith_number, book_number, chapter_id, urn_ar, urn_en, "
                    + "body_ar, body_en, grades, raw";

    private static final RowMapper<SunnahHadithRow> ROW_MAPPER = (rs, rn) -> new SunnahHadithRow(
            rs.getString("collection_name"),
            rs.getString("hadith_number"),
            rs.getString("book_number"),
            rs.getString("chapter_id"),
            rs.getObject("urn_ar", Long.class),
            rs.getObject("urn_en", Long.class),
            rs.getString("body_ar"),
            rs.getString("body_en"),
            rs.getString("grades"),
            rs.getString("raw")
    );

    private final JdbcTemplate jdbcTemplate;

    public SunnahHadithDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<SunnahHadithRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO sn_staging_hadith (
                    collection_name, hadith_number, book_number, chapter_id, urn_ar, urn_en,
                    body_ar, body_en, grades, raw
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (collection_name, hadith_number) DO UPDATE SET
                    book_number = EXCLUDED.book_number,
                    chapter_id = EXCLUDED.chapter_id,
                    urn_ar = EXCLUDED.urn_ar,
                    urn_en = EXCLUDED.urn_en,
                    body_ar = EXCLUDED.body_ar,
                    body_en = EXCLUDED.body_en,
                    grades = EXCLUDED.grades,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.collectionName(), r.hadithNumber(), r.bookNumber(), r.chapterId(),
                r.urnAr(), r.urnEn(), r.bodyAr(), r.bodyEn(), r.gradesJson(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public List<SunnahHadithRow> findByCollection(String collectionName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sn_staging_hadith WHERE collection_name = ? "
                        + "ORDER BY hadith_number",
                ROW_MAPPER, collectionName
        );
    }

    public int countByCollection(String collectionName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sn_staging_hadith WHERE collection_name = ?",
                Integer.class, collectionName);
        return count == null ? 0 : count;
    }
}
