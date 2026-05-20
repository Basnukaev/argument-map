package ru.basnukaev.argumentmap.hadith.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.domain.Hadith;

/**
 * Доступ к hd_hadiths. Vision 49d Section 2.6 Phase 1.
 */
@Repository
public class HadithRepository {

    private static final String COLUMNS =
            "id, primary_book_id, primary_number, normalized_matn, status, "
                    + "source_id, metadata, created_at";

    private static final RowMapper<Hadith> ROW_MAPPER = (rs, rn) -> new Hadith(
            rs.getObject("id", UUID.class),
            rs.getObject("primary_book_id", UUID.class),
            (Integer) rs.getObject("primary_number"),
            rs.getString("normalized_matn"),
            rs.getString("status"),
            rs.getObject("source_id", UUID.class),
            rs.getString("metadata"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public HadithRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Hadith save(Hadith h) {
        jdbcTemplate.update(
                "INSERT INTO hd_hadiths (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                h.id(), h.primaryBookId(), h.primaryNumber(), h.normalizedMatn(),
                h.status(), h.sourceId(), h.metadata(), odt(h.createdAt())
        );
        return h;
    }

    public Optional<Hadith> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM hd_hadiths WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    public List<Hadith> findPage(String q, String status, UUID bookId,
                                 int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM hd_hadiths WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND LOWER(normalized_matn) LIKE LOWER(?)");
            args.add("%" + q + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (bookId != null) {
            sql.append(" AND primary_book_id = ?");
            args.add(bookId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long countFiltered(String q, String status, UUID bookId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM hd_hadiths WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND LOWER(normalized_matn) LIKE LOWER(?)");
            args.add("%" + q + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (bookId != null) {
            sql.append(" AND primary_book_id = ?");
            args.add(bookId);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }
}
