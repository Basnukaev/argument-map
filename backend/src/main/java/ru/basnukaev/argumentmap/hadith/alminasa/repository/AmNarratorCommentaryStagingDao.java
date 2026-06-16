package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorCommentaryRow;

/**
 * DAO {@code am_staging_narrator_commentary} (миграция 76, джарх/таʿдиль о рави).
 * PK — doc_id (ES {@code _id}): идемпотентный upsert {@code ON CONFLICT (doc_id)}.
 * Джойн на рави по {@code narrator_id} (= hd_narrators.external_id).
 */
@Repository
public class AmNarratorCommentaryStagingDao {

    private static final RowMapper<AmNarratorCommentaryRow> ROW_MAPPER = (rs, rn) -> new AmNarratorCommentaryRow(
            rs.getString("doc_id"),
            rs.getInt("narrator_id"),
            rs.getString("commenter"),
            rs.getString("book"),
            rs.getString("raw_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public AmNarratorCommentaryStagingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(List<AmNarratorCommentaryRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_narrator_commentary (
                    doc_id, narrator_id, commenter, book, raw
                )
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (doc_id) DO UPDATE SET
                    narrator_id = EXCLUDED.narrator_id,
                    commenter = EXCLUDED.commenter,
                    book = EXCLUDED.book,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.docId(), r.narratorId(), r.commenter(), r.book(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    /** Цитаты по narrator_id (ключ джойна на рави = external_id). */
    public List<AmNarratorCommentaryRow> findByNarratorId(int narratorId) {
        return jdbcTemplate.query(
                "SELECT doc_id, narrator_id, commenter, book, raw::text AS raw_json "
                        + "FROM am_staging_narrator_commentary WHERE narrator_id = ?",
                ROW_MAPPER, narratorId);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_narrator_commentary", Integer.class);
        return count == null ? 0 : count;
    }
}
