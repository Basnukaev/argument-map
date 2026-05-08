package ru.basnukaev.argumentmap.library.shamela.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShamelaSyncStateDaoIT {

    @Autowired
    private ShamelaSyncStateDao dao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSingleton() {
        // singleton-строка создаётся миграцией, после предыдущих тестов
        // мы могли её удалить или модифицировать - откатываем к initial state
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_sync_state WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO lib_shamela_sync_state (id) VALUES (1)");
        }
        jdbcTemplate.update(
                "UPDATE lib_shamela_sync_state SET master_version = 0, last_synced_at = NULL WHERE id = 1");
    }

    @AfterEach
    void restoreSingleton() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_shamela_sync_state WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO lib_shamela_sync_state (id) VALUES (1)");
        }
    }

    @Test
    void getMasterVersion_returns_initial_zero() {
        assertThat(dao.getMasterVersion()).isZero();
    }

    @Test
    void updateMasterVersion_persists() {
        dao.updateMasterVersion(1261);

        assertThat(dao.getMasterVersion()).isEqualTo(1261);
    }

    @Test
    void updateMasterVersion_sets_last_synced_at() {
        assertThat(dao.getLastSyncedAt()).isEmpty();

        dao.updateMasterVersion(42);

        Optional<OffsetDateTime> lastSyncedAt = dao.getLastSyncedAt();
        assertThat(lastSyncedAt).isPresent();
    }

    @Test
    void updateMasterVersion_throws_if_singleton_missing() {
        jdbcTemplate.update("DELETE FROM lib_shamela_sync_state WHERE id = 1");

        assertThatThrownBy(() -> dao.updateMasterVersion(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("singleton");
    }

    @Test
    void getLastSyncedAt_returns_empty_when_never_synced() {
        assertThat(dao.getLastSyncedAt()).isEmpty();
    }
}
