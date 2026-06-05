package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import static ru.basnukaev.argumentmap.hadith.alminasa.repository.AmDaoSupport.sumAffected;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmCommentaryRow;

/** DAO {@code am_staging_commentary} (миграция 75, علل/иляль). План 8 alminasa. */
@Repository
public class AmCommentaryStagingDao {

    private static final RowMapper<AmCommentaryRow> ROW_MAPPER = (rs, rn) -> new AmCommentaryRow(
            rs.getInt("commentary_id"),
            rs.getString("book_name"),
            rs.getString("author_name"),
            rs.getString("narrations_json"),
            rs.getString("raw_json")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AmCommentaryStagingDao(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public int upsertAll(List<AmCommentaryRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO am_staging_commentary (
                    commentary_id, book_name, author_name, narrations, raw
                )
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (commentary_id) DO UPDATE SET
                    book_name = EXCLUDED.book_name,
                    author_name = EXCLUDED.author_name,
                    narrations = EXCLUDED.narrations,
                    raw = EXCLUDED.raw,
                    imported_at = now()
                """;
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.commentaryId(), r.bookName(), r.authorName(), r.narrationsJson(), r.rawJson()
        }).toList();
        return sumAffected(jdbcTemplate.batchUpdate(sql, args));
    }

    /**
     * Комментарии-иляль, чей массив {@code narrations} содержит данный
     * hadith_id (ключ джойна на хадис). Bind — JSON-массив {@code ["146-2"]},
     * сериализованный Jackson'ом (НЕ конкатенация — иначе спецсимволы в id
     * сломали бы jsonb-литерал). GIN-индекс покрывает {@code @>}.
     */
    public List<AmCommentaryRow> findByNarration(String externalId) {
        String arrayLiteral;
        try {
            arrayLiteral = objectMapper.writeValueAsString(List.of(externalId));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "не удалось сериализовать narration-фильтр: " + externalId, e);
        }
        return jdbcTemplate.query(
                "SELECT commentary_id, book_name, author_name, "
                        + "narrations::text AS narrations_json, raw::text AS raw_json "
                        + "FROM am_staging_commentary WHERE narrations @> ?::jsonb",
                ROW_MAPPER, arrayLiteral);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM am_staging_commentary", Integer.class);
        return count == null ? 0 : count;
    }
}
