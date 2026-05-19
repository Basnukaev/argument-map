package ru.basnukaev.argumentmap.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.auth.repository.RefreshTokenRepository;

/**
 * Cron janitor для cleanup'а таблицы {@code refresh_tokens} (ADR-047
 * follow-up, Security backlog).
 *
 * <p>Удаляет refresh-записи старше {@code refresh-token.cleanup.retention-days}
 * (default 30): revoked токены где {@code revoked_at < cutoff} и
 * expired активные токены {@code expires_at < cutoff}. Без cleanup'а
 * таблица растёт линейно от login activity (миллионы revoked rows за
 * год), что замедляет lookup по {@code token_hash} (UNIQUE index) и
 * раздувает БД.
 *
 * <p>Запускается по cron из {@code refresh-token.cleanup.cron} (default
 * {@code 0 30 2 * * *} - 02:30 ежедневно, после AuditLogRetentionJanitor
 * в 02:00 и до orphan janitor в 03:00).
 *
 * <p>Активируется через {@code refresh-token.cleanup.enabled=true} - по
 * умолчанию выключен (dev/test/staging работают без janitor'а).
 * Минимум retention = 7 дней (валидация в
 * {@link RefreshTokenCleanupProperties}).
 *
 * <p>Hard DELETE без soft-delete: refresh-история редко нужна для
 * forensics, retention window достаточно для post-mortem stolen tokens.
 * Если понадобится restoration - backup-based recovery.
 *
 * <p><b>Без {@code @Transactional}</b> на {@code @Scheduled} (см.
 * {@code antipatterns.md}) - JdbcTemplate выполнит DELETE в auto-commit
 * одним statement'ом.
 */
@Component
@ConditionalOnProperty(prefix = "refresh-token.cleanup", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RefreshTokenCleanupProperties.class)
public class RefreshTokenCleanupJanitor {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJanitor.class);

    private final RefreshTokenRepository repository;
    private final RefreshTokenCleanupProperties properties;

    public RefreshTokenCleanupJanitor(
            RefreshTokenRepository repository,
            RefreshTokenCleanupProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Главный entry-point. Запускается по cron из properties. Возвращает
     * количество удалённых строк - используется тестами и опционально
     * exposed через admin endpoint в будущем.
     */
    @Scheduled(cron = "${refresh-token.cleanup.cron:0 30 2 * * *}")
    public int cleanup() {
        Instant startedAt = Instant.now();
        Instant cutoff = startedAt.minus(properties.retentionDays(), ChronoUnit.DAYS);

        int deleted = repository.deleteOlderThan(cutoff);

        Duration elapsed = Duration.between(startedAt, Instant.now());
        log.info(
                "RefreshTokenCleanupJanitor: cleanup завершён за {}ms. "
                        + "удалено {} строк старше {} дней (cutoff={})",
                elapsed.toMillis(), deleted, properties.retentionDays(), cutoff);
        return deleted;
    }
}
