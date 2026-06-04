package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;

/** DAO {@code am_staging_hadith} (миграция 72). План 2 alminasa. */
@Repository
public class AmHadithStagingDao {

    private static final RowMapper<AmHadithRow> ROW_MAPPER = (rs, rn) -> new AmHadithRow(
            rs.getString("hadith_id"),
            rs.getInt("book_id"),
            rs.getLong("hadith_serial_id"),
            rs.getString("book_name"),
            rs.getString("hadith_type"),
            rs.getString("chapter"),
            rs.getString("sub_chapter"),
            rs.getString("raw_json")
    );

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

    /**
     * Keyset-пагинация по составному курсору {@code (book_id, hadith_serial_id)}.
     * При {@code afterBookId == null} возвращает строки с начала.
     * Порядок: {@code ORDER BY book_id, hadith_serial_id} — детерминированный
     * обход staging для маппера (план 3).
     */
    public List<AmHadithRow> findPage(Integer afterBookId, Long afterSerial, int limit) {
        if (afterBookId == null) {
            return jdbcTemplate.query(
                    "SELECT hadith_id, book_id, hadith_serial_id, book_name, hadith_type, "
                            + "chapter, sub_chapter, raw::text AS raw_json "
                            + "FROM am_staging_hadith "
                            + "ORDER BY book_id, hadith_serial_id "
                            + "LIMIT ?",
                    ROW_MAPPER, limit);
        }
        return jdbcTemplate.query(
                "SELECT hadith_id, book_id, hadith_serial_id, book_name, hadith_type, "
                        + "chapter, sub_chapter, raw::text AS raw_json "
                        + "FROM am_staging_hadith "
                        + "WHERE (book_id, hadith_serial_id) > (?, ?) "
                        + "ORDER BY book_id, hadith_serial_id "
                        + "LIMIT ?",
                ROW_MAPPER, afterBookId, afterSerial, limit);
    }

    /** Поиск по природному ключу {@code hadith_id}. */
    public Optional<AmHadithRow> findById(String hadithId) {
        return jdbcTemplate.query(
                "SELECT hadith_id, book_id, hadith_serial_id, book_name, hadith_type, "
                        + "chapter, sub_chapter, raw::text AS raw_json "
                        + "FROM am_staging_hadith WHERE hadith_id = ?",
                ROW_MAPPER, hadithId
        ).stream().findFirst();
    }

    /**
     * Число строк по каждому сборнику — для сводки прогресса краулинга
     * (план 5, admin-endpoint). Один GROUP BY вместо N запросов.
     */
    public Map<Integer, Long> countByBookId() {
        Map<Integer, Long> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT book_id, COUNT(*) AS cnt FROM am_staging_hadith GROUP BY book_id",
                rs -> {
                    result.put(rs.getInt("book_id"), rs.getLong("cnt"));
                });
        return result;
    }

    /**
     * Каталог застейдженных сборников для admin-страницы импорта (план 5):
     * {@code book_id}, имя сборника (max — book_name внутри группы одинаков)
     * и число застейдженных доков. Один GROUP BY. {@code bookName} может быть
     * {@code null}, если crawl его не записал.
     */
    public List<StagedBook> catalogByBook() {
        return jdbcTemplate.query(
                "SELECT book_id, MAX(book_name) AS book_name, COUNT(*) AS staged_count "
                        + "FROM am_staging_hadith GROUP BY book_id",
                (rs, rn) -> new StagedBook(
                        rs.getInt("book_id"),
                        rs.getString("book_name"),
                        rs.getLong("staged_count")));
    }

    /** Строка каталога staging-сборника: id, имя (nullable), число доков. */
    public record StagedBook(int bookId, String bookName, long stagedCount) {
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_hadith", Integer.class);
        return count == null ? 0 : count;
    }
}
