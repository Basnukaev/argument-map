package ru.basnukaev.argumentmap.hadith.sunnah.repository;

import static ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;

/**
 * DAO staging-таблицы {@code sn_staging_chapter}. Phase 5 ETL шаг 2.
 */
@Repository
public class SunnahChapterDao {

    private static final String COLUMNS =
            "collection_name, book_number, chapter_id, chapter_number_ar, chapter_number_en, "
                    + "title_ar, title_en, intro_ar, intro_en, ending_ar, ending_en, raw";

    private static final RowMapper<SunnahChapterRow> ROW_MAPPER = (rs, rn) -> new SunnahChapterRow(
            rs.getString("collection_name"),
            rs.getString("book_number"),
            rs.getString("chapter_id"),
            rs.getString("chapter_number_ar"),
            rs.getString("chapter_number_en"),
            rs.getString("title_ar"),
            rs.getString("title_en"),
            rs.getString("intro_ar"),
            rs.getString("intro_en"),
            rs.getString("ending_ar"),
            rs.getString("ending_en"),
            rs.getString("raw")
    );

    private final JdbcTemplate jdbcTemplate;

    public SunnahChapterDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<SunnahChapterRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO sn_staging_chapter (
                    collection_name, book_number, chapter_id, chapter_number_ar, chapter_number_en,
                    title_ar, title_en, intro_ar, intro_en, ending_ar, ending_en, raw
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (collection_name, book_number, chapter_id) DO UPDATE SET
                    chapter_number_ar = EXCLUDED.chapter_number_ar,
                    chapter_number_en = EXCLUDED.chapter_number_en,
                    title_ar = EXCLUDED.title_ar,
                    title_en = EXCLUDED.title_en,
                    intro_ar = EXCLUDED.intro_ar,
                    intro_en = EXCLUDED.intro_en,
                    ending_ar = EXCLUDED.ending_ar,
                    ending_en = EXCLUDED.ending_en,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.collectionName(), r.bookNumber(), r.chapterId(),
                r.chapterNumberAr(), r.chapterNumberEn(),
                r.titleAr(), r.titleEn(), r.introAr(), r.introEn(),
                r.endingAr(), r.endingEn(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public List<SunnahChapterRow> findByCollection(String collectionName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sn_staging_chapter WHERE collection_name = ? "
                        + "ORDER BY book_number, chapter_id",
                ROW_MAPPER, collectionName
        );
    }

    public int countByCollection(String collectionName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sn_staging_chapter WHERE collection_name = ?",
                Integer.class, collectionName);
        return count == null ? 0 : count;
    }
}
