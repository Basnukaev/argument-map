package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;

/** DAO {@code am_staging_hadith} (миграция 72). План 2 alminasa. */
@Repository
public class AmHadithStagingDao {

    private final JdbcTemplate jdbcTemplate;

    public AmHadithStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmHadithRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_hadith (
                    hadith_id, book_id, hadith_serial_id, book_name, hadith_type,
                    chapter, sub_chapter, raw
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (hadith_id) DO UPDATE SET
                    book_id = EXCLUDED.book_id,
                    hadith_serial_id = EXCLUDED.hadith_serial_id,
                    book_name = EXCLUDED.book_name,
                    hadith_type = EXCLUDED.hadith_type,
                    chapter = EXCLUDED.chapter,
                    sub_chapter = EXCLUDED.sub_chapter,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.hadithId(), r.bookId(), r.hadithSerialId(), r.bookName(), r.hadithType(),
                r.chapter(), r.subChapter(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_hadith", Integer.class);
        return count == null ? 0 : count;
    }
}
