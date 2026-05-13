package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;

@Repository
public class SourceRepository {

    private static final String COLUMNS =
            "id, source_type, title, citation, reliability, authority_id, book_id, metadata, created_at";

    private static final RowMapper<Source> ROW_MAPPER = (rs, rn) -> {
        String reliability = rs.getString("reliability");
        return new Source(
                rs.getObject("id", UUID.class),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("title"),
                rs.getString("citation"),
                reliability == null ? null : Reliability.valueOf(reliability),
                rs.getObject("authority_id", UUID.class),
                rs.getObject("book_id", UUID.class),
                rs.getString("metadata"),
                instant(rs, "created_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public SourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Source save(Source source) {
        jdbcTemplate.update(
                "INSERT INTO sources (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                source.id(),
                source.sourceType().name(),
                source.title(),
                source.citation(),
                source.reliability() == null ? null : source.reliability().name(),
                source.authorityId(),
                source.bookId(),
                source.metadata(),
                odt(source.createdAt())
        );
        return source;
    }

    public Optional<Source> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sources WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<Source> findByBookId(UUID bookId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sources WHERE book_id = ?",
                ROW_MAPPER,
                bookId
        ).stream().findFirst();
    }

    public List<Source> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sources ORDER BY created_at",
                ROW_MAPPER
        );
    }

    public List<Source> searchByTitle(String query) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sources WHERE title ILIKE ? ORDER BY title",
                ROW_MAPPER,
                "%" + query + "%"
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM sources WHERE id = ?", id) > 0;
    }

    /**
     * Атомарный ensure-or-create по unique index (source_type, book_id).
     * При race-condition двух concurrent INSERT'ов на одну книгу один
     * выигрывает, второй получает existing row через DO NOTHING +
     * findByBookId.
     *
     * <p>Требует {@code sourceType=BOOK} и не-null {@code bookId} -
     * другие комбинации идут через обычный {@link #save(Source)}.
     */
    public Source upsertByBookId(Source source) {
        if (source.bookId() == null || source.sourceType() != SourceType.BOOK) {
            throw new IllegalArgumentException(
                "upsertByBookId требует sourceType=BOOK и не-null bookId, получено: "
                    + source.sourceType() + " / " + source.bookId());
        }
        Integer affected = jdbcTemplate.update(
                "INSERT INTO sources (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) "
                        + "ON CONFLICT (source_type, book_id) WHERE book_id IS NOT NULL DO NOTHING",
                source.id(),
                source.sourceType().name(),
                source.title(),
                source.citation(),
                source.reliability() == null ? null : source.reliability().name(),
                source.authorityId(),
                source.bookId(),
                source.metadata(),
                odt(source.createdAt())
        );
        if (affected != null && affected > 0) {
            return source;
        }
        return findByBookId(source.bookId()).orElseThrow(() ->
            new IllegalStateException("UPSERT conflict но findByBookId empty - inconsistent state"));
    }
}
