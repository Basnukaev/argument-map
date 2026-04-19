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
            "id, source_type, title, citation, reliability, metadata, created_at";

    private static final RowMapper<Source> ROW_MAPPER = (rs, rn) -> {
        String reliability = rs.getString("reliability");
        return new Source(
                rs.getObject("id", UUID.class),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("title"),
                rs.getString("citation"),
                reliability == null ? null : Reliability.valueOf(reliability),
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
                "INSERT INTO sources (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
                source.id(),
                source.sourceType().name(),
                source.title(),
                source.citation(),
                source.reliability() == null ? null : source.reliability().name(),
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
}
