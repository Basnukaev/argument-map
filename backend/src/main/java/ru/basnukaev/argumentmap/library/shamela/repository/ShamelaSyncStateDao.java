package ru.basnukaev.argumentmap.library.shamela.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * DAO для singleton-таблицы {@code lib_shamela_sync_state}. Запись с
 * {@code id=1} создаётся миграцией 17, операции работают только с
 * этой одной строкой. Если её нет (программная инвариантная ошибка
 * - например удалили вручную) - {@link #updateMasterVersion(int)}
 * бросает {@link IllegalStateException}.
 */
@Repository
public class ShamelaSyncStateDao {

    private static final Logger log = LoggerFactory.getLogger(ShamelaSyncStateDao.class);

    private final JdbcTemplate jdbcTemplate;

    public ShamelaSyncStateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int getMasterVersion() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT master_version FROM lib_shamela_sync_state WHERE id = 1",
                Integer.class
        );
        return value == null ? 0 : value;
    }

    public void updateMasterVersion(int version) {
        OffsetDateTime nowUtc = odt(Instant.now());
        int updated = jdbcTemplate.update(
                "UPDATE lib_shamela_sync_state SET master_version = ?, last_synced_at = ? WHERE id = 1",
                version, nowUtc
        );
        if (updated != 1) {
            throw new IllegalStateException("lib_shamela_sync_state singleton отсутствует");
        }
        log.info("shamela sync state updated: master_version={}", version);
    }

    public Optional<OffsetDateTime> getLastSyncedAt() {
        return jdbcTemplate.query(
                "SELECT last_synced_at FROM lib_shamela_sync_state WHERE id = 1",
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    OffsetDateTime value = rs.getObject("last_synced_at", OffsetDateTime.class);
                    return Optional.ofNullable(value);
                }
        );
    }
}
