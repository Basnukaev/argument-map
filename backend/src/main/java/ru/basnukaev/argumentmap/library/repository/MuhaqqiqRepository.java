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

import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;

@Repository
public class MuhaqqiqRepository {

    private static final String COLUMNS = "id, name, full_name, created_at";

    private static final RowMapper<Muhaqqiq> ROW_MAPPER = (rs, rn) -> new Muhaqqiq(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("full_name"),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public MuhaqqiqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Muhaqqiq save(Muhaqqiq muhaqqiq) {
        jdbcTemplate.update(
                "INSERT INTO lib_muhaqqiqs (" + COLUMNS + ") VALUES (?, ?, ?, ?)",
                muhaqqiq.id(),
                muhaqqiq.name(),
                muhaqqiq.fullName(),
                odt(muhaqqiq.createdAt())
        );
        return muhaqqiq;
    }

    public Optional<Muhaqqiq> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_muhaqqiqs WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public Optional<Muhaqqiq> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_muhaqqiqs WHERE name = ?",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    /**
     * Helper для ETL: создаёт row только с short name (fullName = null),
     * если ETL парсер позже найдёт полное имя - можно обновить через save
     * separate row (нет update operation в этой репе).
     */
    public UUID findOrCreate(String name) {
        return findByName(name)
                .map(Muhaqqiq::id)
                .orElseGet(() -> save(new Muhaqqiq(UUID.randomUUID(), name, null, Instant.now())).id());
    }

    public List<Muhaqqiq> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM lib_muhaqqiqs ORDER BY name",
                ROW_MAPPER
        );
    }
}
