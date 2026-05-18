package ru.basnukaev.argumentmap.auth.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.auth.domain.UserPreference;

/**
 * JDBC-доступ к user_preferences. Upsert через INSERT ON CONFLICT
 * (user_id, key) DO UPDATE - один ключ на пользователя, повторная
 * запись обновляет значение и updated_at.
 *
 * value хранится в jsonb колонке - чтобы Postgres не падал на cast
 * String → jsonb используется явный CAST(? AS jsonb).
 */
@Repository
public class UserPreferenceRepository {

    private static final String COLUMNS = "id, user_id, key, value::text AS value, updated_at";

    private static final RowMapper<UserPreference> ROW_MAPPER = (rs, rn) -> new UserPreference(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("key"),
            rs.getString("value"),
            instant(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public UserPreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserPreference> findByUserId(UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_preferences WHERE user_id = ? ORDER BY key",
                ROW_MAPPER,
                userId
        );
    }

    public Optional<UserPreference> findByUserAndKey(UUID userId, String key) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_preferences WHERE user_id = ? AND key = ?",
                ROW_MAPPER,
                userId, key
        ).stream().findFirst();
    }

    /**
     * Upsert: вставка новой пары или обновление существующей по
     * UNIQUE(user_id, key). Значение - валидный JSON-литерал (например
     * "\"ru\"" для строки, "true" для boolean, "42" для числа).
     */
    public void upsert(UUID userId, String key, String jsonValue) {
        jdbcTemplate.update(
                "INSERT INTO user_preferences (user_id, key, value) "
                        + "VALUES (?, ?, CAST(? AS jsonb)) "
                        + "ON CONFLICT (user_id, key) DO UPDATE "
                        + "SET value = EXCLUDED.value, updated_at = now()",
                userId, key, jsonValue
        );
    }

    public void delete(UUID userId, String key) {
        jdbcTemplate.update(
                "DELETE FROM user_preferences WHERE user_id = ? AND key = ?",
                userId, key
        );
    }
}
