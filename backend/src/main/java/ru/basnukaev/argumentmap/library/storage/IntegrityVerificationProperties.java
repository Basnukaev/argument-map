package ru.basnukaev.argumentmap.library.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Конфиг integrity-verification cron'а (ADR-024, Этап 25.b 6-й пункт).
 * Соответствует блоку {@code storage.integrity:} в {@code application.yml}.
 * Используется {@link IntegrityVerificationJob}.
 *
 * <p>{@code enabled} и {@code cron} читаются Spring инфраструктурой
 * напрямую через {@code @ConditionalOnProperty} и {@code @Scheduled}
 * SpEL-выражения соответственно. Сам record хранит только
 * {@code delayMillis} (нужно injection в job для runtime throttling).
 *
 * @param delayMillis пауза между files в sweep'е. {@code >0} = throttle
 *                    нагрузки на S3 endpoint. {@code 0} - для тестов или
 *                    маленьких библиотек где throttle не нужен. По
 *                    умолчанию 100ms даёт ~10 files/sec cap
 */
@ConfigurationProperties(prefix = "storage.integrity")
public record IntegrityVerificationProperties(
        @DefaultValue("100") long delayMillis
) {
}
