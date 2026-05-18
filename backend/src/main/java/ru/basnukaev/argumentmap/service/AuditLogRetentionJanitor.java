package ru.basnukaev.argumentmap.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.repository.AuditLogRepository;

/**
 * Cron janitor для retention-policy {@code audit_log} (Code review
 * round 3 #5, ADR-043 Amendment 3 - originally TBD).
 *
 * <p>Удаляет записи старше {@code audit.retention.retention-days}
 * (default 365 - разумный compliance window для большинства regimes).
 * Запускается по cron из {@code audit.retention.cron} (default
 * {@code 0 0 2 * * *} - 02:00 ежедневно: после ночных backup'ов и
 * перед orphan janitor'ом в 03:00).
 *
 * <p>Активируется через {@code audit.retention.enabled=true} - по
 * умолчанию выключен (compliance retention - prod-only concern).
 * Минимум retention = 7 дней (валидация в {@link AuditRetentionProperties}).
 *
 * <p>Hard DELETE без soft-delete: audit-rows консумятся редко (manual
 * forensics), soft-delete column раздул бы таблицу + индексы. Если
 * понадобится restoration - backup-based recovery.
 *
 * <p><b>Без {@code @Transactional}</b> на {@code @Scheduled} (см.
 * {@code antipatterns.md}) - JdbcTemplate выполнит DELETE в
 * auto-commit одним statement'ом.
 */
@Component
@ConditionalOnProperty(prefix = "audit.retention", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class AuditLogRetentionJanitor {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionJanitor.class);

    private final AuditLogRepository repository;
    private final AuditRetentionProperties properties;

    public AuditLogRetentionJanitor(
            AuditLogRepository repository,
            AuditRetentionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Главный entry-point. Запускается по cron из properties. Возвращает
     * количество удалённых строк - используется тестами и опционально
     * exposed через admin endpoint в будущем.
     */
    @Scheduled(cron = "${audit.retention.cron:0 0 2 * * *}")
    public int cleanup() {
        Instant startedAt = Instant.now();
        Instant cutoff = startedAt.minus(properties.retentionDays(), ChronoUnit.DAYS);

        int deleted = repository.deleteOlderThan(cutoff);

        Duration elapsed = Duration.between(startedAt, Instant.now());
        log.info(
                "AuditLogRetentionJanitor: cleanup завершён за {}ms. "
                        + "удалено {} строк старше {} дней (cutoff={})",
                elapsed.toMillis(), deleted, properties.retentionDays(), cutoff);
        return deleted;
    }
}
