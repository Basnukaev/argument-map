package ru.basnukaev.argumentmap.auth.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.auth.domain.User;

/**
 * Доступ к users (ADR-040). JDBC Template, без JPA (как и весь проект).
 * Email/username сравнение case-insensitive: LOWER(email)=LOWER(?).
 */
@Repository
public class UserRepository {

    private static final String COLUMNS =
            "id, username, email, password_hash, role, enabled, created_at, updated_at";

    private static final RowMapper<User> ROW_MAPPER = (rs, rn) -> new User(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getBoolean("enabled"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public User save(User user) {
        jdbcTemplate.update(
                "INSERT INTO users (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                user.id(),
                user.username(),
                user.email(),
                user.passwordHash(),
                user.role(),
                user.enabled(),
                odt(user.createdAt()),
                odt(user.updatedAt())
        );
        return user;
    }

    public Optional<User> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM users WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    /**
     * Bulk lookup users по списку id - один SQL вместо N+1 (см.
     * {@code AuditLogController#toResponses} для enrichment страницы
     * audit-log username'ами). Пустой input → empty map без запроса.
     * Missing id'ы просто отсутствуют в результате - caller сам решает
     * как обрабатывать (fallback на null/"unknown").
     */
    public Map<UUID, User> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        return namedJdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM users WHERE id IN (:ids)",
                params,
                ROW_MAPPER
        ).stream().collect(Collectors.toMap(User::id, Function.identity()));
    }

    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM users WHERE LOWER(email) = LOWER(?)",
                ROW_MAPPER,
                email
        ).stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM users WHERE username = ?",
                ROW_MAPPER,
                username
        ).stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    public void updatePassword(UUID userId, String newPasswordHash) {
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, updated_at = now() WHERE id = ?",
                newPasswordHash, userId
        );
    }

    public void setEnabled(UUID userId, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE users SET enabled = ?, updated_at = now() WHERE id = ?",
                enabled, userId
        );
    }
}
