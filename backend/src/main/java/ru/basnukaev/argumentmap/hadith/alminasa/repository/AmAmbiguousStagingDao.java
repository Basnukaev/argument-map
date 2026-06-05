package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmAmbiguousRow;

/** DAO {@code am_staging_ambiguous} (миграция 75, غريب/гариб). План 8 alminasa. */
@Repository
public class AmAmbiguousStagingDao {

    private static final RowMapper<AmAmbiguousRow> ROW_MAPPER = (rs, rn) -> new AmAmbiguousRow(
            rs.getInt("ambiguous_id"),
            rs.getString("book_name"),
            rs.getString("author"),
            rs.getString("raw_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public AmAmbiguousStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmAmbiguousRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_ambiguous (
                    ambiguous_id, book_name, author, raw
                )
                VALUES (?, ?, ?, ?::jsonb)
                ON CONFLICT (ambiguous_id) DO UPDATE SET
                    book_name = EXCLUDED.book_name,
                    author = EXCLUDED.author,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.ambiguousId(), r.bookName(), r.author(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    /**
     * Словарные статьи по их id (id из hadith-дока {@code ambiguous[].explanation_ids}).
     * Пустой список → пустой результат без запроса.
     */
    public List<AmAmbiguousRow> findByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        return jdbcTemplate.query(
                "SELECT ambiguous_id, book_name, author, raw::text AS raw_json "
                        + "FROM am_staging_ambiguous WHERE ambiguous_id IN (" + placeholders + ")",
                ROW_MAPPER, ids.toArray());
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_ambiguous", Integer.class);
        return count == null ? 0 : count;
    }
}
