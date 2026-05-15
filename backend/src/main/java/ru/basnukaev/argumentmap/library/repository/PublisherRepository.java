package ru.basnukaev.argumentmap.library.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.library.domain.Publisher;

@Repository
public class PublisherRepository {

    private static final String COLUMNS = "id, name, created_at";

    private static final RowMapper<Publisher> ROW_MAPPER = (rs, rn) -> new Publisher(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public PublisherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Publisher save(Publisher publisher) {
        jdbcTemplate.update(
                "INSERT INTO lib_publishers (" + COLUMNS + ") VALUES (?, ?, ?)",
                publisher.id(),
                publisher.name(),
                odt(publisher.createdAt())
        );
        return publisher;
    }

    public Optional<Publisher> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<Publisher> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers WHERE name = ?",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    /**
     * Helper для ETL: если издатель с таким именем уже есть - возвращает его id,
     * иначе создаёт новый row + возвращает свежий id. Идемпотентен для повторных
     * вызовов с тем же именем.
     */
    public UUID findOrCreate(String name) {
        return findByName(name)
                .map(Publisher::id)
                .orElseGet(() -> save(new Publisher(UUID.randomUUID(), name, Instant.now())).id());
    }

    public List<Publisher> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers ORDER BY name",
                ROW_MAPPER
        );
    }

    /**
     * Autocomplete-поиск для UI BookEditModal (Этап 20.d).
     */
    public List<Publisher> searchByName(String query, int limit) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publishers WHERE name ILIKE ? "
                        + "ORDER BY name LIMIT ?",
                ROW_MAPPER,
                "%" + query + "%",
                limit
        );
    }
}
