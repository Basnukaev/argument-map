package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.AuthorityType;

@Repository
public class AuthorityRepository {

    private static final String COLUMNS =
            "id, name, bio, era, madhab, metadata, created_at, full_name, death_year_hijri, type";

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
                deathYearOrNull,
                rs.getString("type")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public AuthorityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Authority save(Authority authority) {
        // type null → 'SCHOLAR' через DB default (backward compat для
        // existing callers, не передающих type). Whitelist enforced
        // через CHECK constraint в миграции 47
        String type = authority.type() == null ? AuthorityType.SCHOLAR : authority.type();
        jdbcTemplate.update(
                "INSERT INTO authorities (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)",
                authority.id(),
                authority.name(),
                authority.bio(),
                authority.era(),
                authority.madhab(),
                authority.metadata(),
                odt(authority.createdAt()),
                authority.fullName(),
                authority.deathYearHijri(),
                type
        );
        return new Authority(
                authority.id(), authority.name(), authority.bio(),
                authority.era(), authority.madhab(), authority.metadata(),
                authority.createdAt(), authority.fullName(),
                authority.deathYearHijri(), type
        );
    }

    public Optional<Authority> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    /**
     * Batch-загрузка authorities по набору id. Один SQL вместо N findById.
     */
    public List<Authority> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM authorities WHERE id IN (" + placeholders + ")",
                ROW_MAPPER,
                ids.toArray()
        );
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
     * Пагинированный список с фильтрами. era - exact match (varchar в схеме,
     * без enum-whitelist - в проекте свободный текст типа "XIII-XIV век").
     * Сортировка: name (исторический порядок для справочника учёных).
     */
    public List<Authority> findPage(String query, String era, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM authorities WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, era);
        sql.append(" ORDER BY name LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public long countFiltered(String query, String era) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM authorities WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, era);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private static void appendFilters(StringBuilder sql, List<Object> args,
                                      String query, String era) {
        if (query != null && !query.isBlank()) {
            sql.append(" AND name ILIKE ?");
            args.add("%" + query + "%");
        }
        if (era != null && !era.isBlank()) {
            sql.append(" AND era = ?");
            args.add(era);
        }
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

    /**
     * Partial update через COALESCE: null-поле в параметре = «не менять».
     * Возвращает количество затронутых строк (0 если id не найден).
     * Вызывающий код должен проверить результат и бросить
     * {@link ru.basnukaev.argumentmap.exception.AuthorityNotFoundException}
     * при 0.
     */
    public int update(UUID id, String name, String bio, String era,
                      String madhab, String type, String metadataJson) {
        return jdbcTemplate.update(
                "UPDATE authorities SET"
                        + " name     = COALESCE(?, name),"
                        + " bio      = COALESCE(?, bio),"
                        + " era      = COALESCE(?, era),"
                        + " madhab   = COALESCE(?, madhab),"
                        + " type     = COALESCE(?, type),"
                        + " metadata = COALESCE(?::jsonb, metadata)"
                        + " WHERE id = ?",
                name, bio, era, madhab, type, metadataJson, id
        );
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM authorities WHERE id = ?", id) > 0;
    }
}
