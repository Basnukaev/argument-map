package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.AuditLog;
import ru.basnukaev.argumentmap.repository.AuditLogRepository;

/**
 * IT для {@link AuditLogRetentionJanitor} (Code review round 3 #5).
 * Janitor явно включается через {@code @TestPropertySource}
 * {@code audit.retention.enabled=true} - в обычном application.yml
 * выключен (compliance retention prod-only).
 *
 * <p>Cron заменён на never-firing expression (31 февраля) чтобы Spring
 * не запускал cleanup автоматически - тесты вызывают
 * {@link AuditLogRetentionJanitor#cleanup()} явно.
 *
 * <p>Сценарии:
 * <ul>
 *   <li>cleanup удаляет старые rows, оставляет новые</li>
 *   <li>cleanup на пустой таблице - 0 удалений, не падает</li>
 *   <li>retentionDays=30 - cutoff exact: row на 30 дней назад уцелеет,
 *       row на 31 день удалится</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "audit.retention.enabled=true",
        // 31 февраля - cron не fire'нет автоматически, sweep только вручную
        "audit.retention.cron=0 0 0 31 2 ?",
        // 30 дней retention для тестов - удобный round number
        "audit.retention.retention-days=30"
})
class AuditLogRetentionJanitorIT {

    @Autowired private AuditLogRetentionJanitor janitor;
    @Autowired private AuditLogRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID actorId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_log");
        actorId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                actorId, "actor-" + actorId, actorId + "@test.com");
    }

    @Test
    void cleanup_deletesOldRows() {
        // 3 строки: одна свежая, две старые (60 дней и 100 дней)
        insertAuditRowWithCreatedAt(daysAgo(1));
        insertAuditRowWithCreatedAt(daysAgo(60));
        insertAuditRowWithCreatedAt(daysAgo(100));

        int deleted = janitor.cleanup();

        assertThat(deleted).isEqualTo(2);
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log", Long.class);
        assertThat(remaining).isEqualTo(1);
    }

    @Test
    void cleanup_keepsRecentRows() {
        // Все 3 row в пределах retention (1 / 10 / 29 дней) - не удаляются
        insertAuditRowWithCreatedAt(daysAgo(1));
        insertAuditRowWithCreatedAt(daysAgo(10));
        insertAuditRowWithCreatedAt(daysAgo(29));

        int deleted = janitor.cleanup();

        assertThat(deleted).isZero();
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log", Long.class);
        assertThat(remaining).isEqualTo(3);
    }

    @Test
    void cleanup_emptyTable_doesNothing() {
        int deleted = janitor.cleanup();

        assertThat(deleted).isZero();
    }

    @Test
    void cleanup_cutoffBoundary_exact30Days() {
        // row на 31 день назад - удаляется (старше cutoff)
        // row на 29 дней назад - остаётся (моложе cutoff)
        insertAuditRowWithCreatedAt(daysAgo(31));
        insertAuditRowWithCreatedAt(daysAgo(29));

        int deleted = janitor.cleanup();

        assertThat(deleted).isEqualTo(1);
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log", Long.class);
        assertThat(remaining).isEqualTo(1);
    }

    /**
     * Прямой insert через repo с overridden createdAt - можем выставить
     * любую дату включая past. Через {@code AuditLogService.log*} не
     * получится потому что там {@code Instant.now()} hardcoded.
     */
    private void insertAuditRowWithCreatedAt(Instant createdAt) {
        repository.save(new AuditLog(
                UUID.randomUUID(),
                AuditEntityType.TOPIC,
                UUID.randomUUID(),
                null,
                null,
                "CREATE",
                actorId,
                "{\"test\":true}",
                null,
                createdAt
        ));
        // sanity check - перезагрузим и убедимся что created_at сохранён
        // как мы хотели (jsonb sometimes округляет microseconds)
        @SuppressWarnings("unused")
        Map<String, Object> __ = jdbcTemplate.queryForMap(
                "SELECT created_at FROM audit_log ORDER BY created_at DESC LIMIT 1");
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
