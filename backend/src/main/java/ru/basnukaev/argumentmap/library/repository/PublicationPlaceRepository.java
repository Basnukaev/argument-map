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

import ru.basnukaev.argumentmap.library.domain.PublicationPlace;

@Repository
public class PublicationPlaceRepository {

    private static final String COLUMNS = "id, name, created_at";

    private static final RowMapper<PublicationPlace> ROW_MAPPER = (rs, rn) -> new PublicationPlace(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public PublicationPlaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PublicationPlace save(PublicationPlace place) {
        jdbcTemplate.update(
                "INSERT INTO lib_publication_places (" + COLUMNS + ") VALUES (?, ?, ?)",
                place.id(),
                place.name(),
                odt(place.createdAt())
        );
        return place;
    }

    public Optional<PublicationPlace> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<PublicationPlace> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places WHERE name = ?",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    public UUID findOrCreate(String name) {
        return findByName(name)
                .map(PublicationPlace::id)
                .orElseGet(() -> save(new PublicationPlace(UUID.randomUUID(), name, Instant.now())).id());
    }

    public List<PublicationPlace> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places ORDER BY name",
                ROW_MAPPER
        );
    }

    /**
     * Autocomplete-поиск для UI BookEditModal (Этап 20.d).
     */
    public List<PublicationPlace> searchByName(String query, int limit) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_publication_places WHERE name ILIKE ? "
                        + "ORDER BY name LIMIT ?",
                ROW_MAPPER,
                "%" + query + "%",
                limit
        );
    }
}
