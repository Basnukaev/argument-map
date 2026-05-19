package ru.basnukaev.argumentmap.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Конфиг cleanup-policy для {@code refresh_tokens} (ADR-047 follow-up).
 * Соответствует блоку {@code refresh-token.cleanup:} в
 * {@code application.yml}. Используется {@link RefreshTokenCleanupJanitor}.
 *
 * <p>Default {@code enabled=false} - на dev/test/staging janitor не
 * запускается (refresh_tokens растёт медленно от login activity, manual
 * cleanup acceptable). В prod включается через
 * {@code REFRESH_TOKEN_CLEANUP_ENABLED=true} - mandatory до production
 * (ADR-047: без cleanup'а таблица растёт линейно).
 *
 * <p>Минимальный {@code retentionDays} - 7 дней (валидация в compact
 * constructor). Меньше = опасно для steal-detection forensics: если
 * stolen refresh используется через неделю после rotation, history
 * нужна для tracing chain. 7 - rolling window достаточный для post-mortem.
 *
 * @param enabled включает {@link RefreshTokenCleanupJanitor} cron job.
 *                По умолчанию {@code false}
 * @param retentionDays через сколько дней refresh-записи удаляются.
 *                      Default {@code 30} - balance между forensics window
 *                      и табличной hygiene. Refresh TTL по умолчанию 7
 *                      дней (ADR-040), retentionDays=30 даёт ~3 рекомендуемых
 *                      window после expiry для forensics
 */
@ConfigurationProperties(prefix = "refresh-token.cleanup")
public record RefreshTokenCleanupProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("30") int retentionDays
) {

    public RefreshTokenCleanupProperties {
        if (enabled && retentionDays < 7) {
            throw new IllegalArgumentException(
                    "refresh-token.cleanup.retention-days=" + retentionDays
                            + " < 7 - слишком короткий retention для steal-detection forensics");
        }
    }
}
