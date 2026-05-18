package ru.basnukaev.argumentmap.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Конфиг retention-policy для audit_log (Code review round 3 #5).
 * Соответствует блоку {@code audit.retention:} в {@code application.yml}.
 * Используется {@link AuditLogRetentionJanitor}.
 *
 * <p>Default {@code enabled=false} - на dev/test/staging janitor не
 * запускается (compliance retention - prod-only concern, на dev audit
 * log может расти безболезненно). В prod включается через
 * {@code AUDIT_RETENTION_ENABLED=true}.
 *
 * <p>Минимальный {@code retentionDays} - 7 дней (валидация в compact
 * constructor). Меньше = опасно для investigation incident'ов: rolling
 * 7-дневное окно даёт минимум для пост-фактум разбора.
 *
 * @param enabled включает {@link AuditLogRetentionJanitor} cron job.
 *                По умолчанию {@code false}
 * @param retentionDays сколько дней audit-записи остаются в БД до
 *                      cleanup'а. {@code created_at < now - days}
 *                      удаляются. Default {@code 365} (1 год -
 *                      разумно для большинства compliance regimes)
 */
@ConfigurationProperties(prefix = "audit.retention")
public record AuditRetentionProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("365") int retentionDays
) {

    public AuditRetentionProperties {
        if (enabled && retentionDays < 7) {
            throw new IllegalArgumentException(
                    "audit.retention.retention-days=" + retentionDays
                            + " < 7 - слишком короткий retention для compliance");
        }
    }
}
