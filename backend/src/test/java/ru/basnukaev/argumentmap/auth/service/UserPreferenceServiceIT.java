package ru.basnukaev.argumentmap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * IT для {@link UserPreferenceService} (Settings screen). Проверяет
 * whitelist валидацию, upsert семантику, изоляцию по user_id.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserPreferenceServiceIT {

    @Autowired
    private UserPreferenceService preferenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        userId = insertUser("primary");
        otherUserId = insertUser("other");
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    @Test
    void set_newKey_creates() {
        Map<String, Object> after = preferenceService.set(userId, "locale", "ar");

        assertThat(after).containsEntry("locale", "ar");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences WHERE user_id = ?",
                Integer.class, userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void set_existingKey_updates() {
        preferenceService.set(userId, "locale", "ru");
        Map<String, Object> after = preferenceService.set(userId, "locale", "ar");

        assertThat(after).containsEntry("locale", "ar");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences WHERE user_id = ? AND key = 'locale'",
                Integer.class, userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void set_invalidKey_throws400() {
        assertThatThrownBy(() -> preferenceService.set(userId, "evilKey", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evilKey");
    }

    @Test
    void set_invalidEnumValue_throws400() {
        assertThatThrownBy(() -> preferenceService.set(userId, "locale", "klingon"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale")
                .hasMessageContaining("klingon");
    }

    @Test
    void set_booleanWithStringValue_throws400() {
        assertThatThrownBy(() -> preferenceService.set(userId, "transliteration", "yes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boolean");
    }

    @Test
    void set_booleanKey_acceptsBoolean() {
        Map<String, Object> after = preferenceService.set(userId, "transliteration", true);
        assertThat(after).containsEntry("transliteration", true);
    }

    @Test
    void getAll_returnsCurrentUserOnly() {
        preferenceService.set(userId, "locale", "ar");
        preferenceService.set(otherUserId, "locale", "en");

        Map<String, Object> mine = preferenceService.getAll(userId);
        Map<String, Object> theirs = preferenceService.getAll(otherUserId);

        assertThat(mine).containsEntry("locale", "ar");
        assertThat(theirs).containsEntry("locale", "en");
    }

    @Test
    void setAll_bulkUpdate_persistsAll() {
        Map<String, Object> updates = Map.of(
                "locale", "ar",
                "textSize", "large",
                "transliteration", true
        );

        Map<String, Object> after = preferenceService.setAll(userId, updates);

        assertThat(after).containsEntry("locale", "ar");
        assertThat(after).containsEntry("textSize", "large");
        assertThat(after).containsEntry("transliteration", true);
    }

    @Test
    void setAll_invalidValueInBulk_rollsBackAll() {
        // Сначала установим один валидный ключ
        preferenceService.set(userId, "locale", "ru");

        // Bulk с одним невалидным - должен откатить (валидация до записи)
        Map<String, Object> updates = Map.of(
                "textSize", "huge",  // невалидный
                "locale", "ar"
        );

        assertThatThrownBy(() -> preferenceService.setAll(userId, updates))
                .isInstanceOf(IllegalArgumentException.class);

        // locale остался прежним
        Map<String, Object> after = preferenceService.getAll(userId);
        assertThat(after).containsEntry("locale", "ru");
    }

    @Test
    void delete_existingKey_removes() {
        preferenceService.set(userId, "locale", "ar");
        preferenceService.delete(userId, "locale");

        Map<String, Object> after = preferenceService.getAll(userId);
        assertThat(after).doesNotContainKey("locale");
    }

    @Test
    void delete_invalidKey_throws400() {
        assertThatThrownBy(() -> preferenceService.delete(userId, "evilKey"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
