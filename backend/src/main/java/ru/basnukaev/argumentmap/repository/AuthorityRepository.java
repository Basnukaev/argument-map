package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Authority;

@Repository
public class AuthorityRepository {

    private static final String COLUMNS =
            "id, name, bio, era, madhab, metadata, created_at, full_name, death_year_hijri";

    private static final RowMapper<Authority> ROW_MAPPER = (rs, rn) -> {
        int deathYear = rs.getInt("death_year_hijri");
        Integer deathYearOrNull = rs.wasNull() ? null : deathYear;
        return new Authority(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("bio"),
                rs.getString("era"),
                rs.getString("madhab"),
                rs.getString("metadata"),
                instant(rs, "created_at"),
                rs.getString("full_name"),
                deathYearOrNull
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public AuthorityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Authority save(Authority authority) {
        jdbcTemplate.update(
                "INSERT INTO authorities (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                authority.id(),
                authority.name(),
                authority.bio(),
                authority.era(),
                authority.madhab(),
                authority.metadata(),
                odt(authority.createdAt()),
                authority.fullName(),
                authority.deathYearHijri()
        );
        return authority;
    }

    public Optional<Authority> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Authority> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities ORDER BY name",
                ROW_MAPPER
        );
    }

    public List<Authority> searchByName(String query) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE name ILIKE ? ORDER BY name",
                ROW_MAPPER,
                "%" + query + "%"
        );
    }

    /**
     * Точное совпадение по имени (без LIKE-маски). Используется ETL-импортом
     * shamela ({@code ShamelaToLibraryMapper}) для дедупликации авторов:
     * имя нормализуется (trim + collapse whitespace) на стороне маппера,
     * затем ищется один-в-один. Возвращает первого найденного - в схеме
     * нет UNIQUE на name, при коллизии берём самого старого.
     */
    public Optional<Authority> findByName(String name) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE name = ? ORDER BY created_at LIMIT 1",
                ROW_MAPPER,
                name
        ).stream().findFirst();
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM authorities WHERE id = ?", id) > 0;
    }
}
