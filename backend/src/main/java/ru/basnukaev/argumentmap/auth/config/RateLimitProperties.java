package ru.basnukaev.argumentmap.auth.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Конфиг rate-limiting для auth endpoints (ADR-046, Security backlog #1).
 * Соответствует блоку {@code auth.rate-limit:} в {@code application.yml}.
 * Используется {@link ru.basnukaev.argumentmap.auth.web.security.RateLimitFilter}.
 *
 * <p>Default {@code enabled=false} - в dev/test/local не активен. В prod
 * включается через env {@code AUTH_RATE_LIMIT_ENABLED=true}. Логика
 * sliding-window 1 минута + lockout при превышении.
 *
 * <p>Whitelist - набор IP откуда лимиты не применяются (CI / health
 * probe / load tests). По умолчанию localhost (127.0.0.1, IPv6 ::1)
 * чтобы dev / smoke не блокировались.
 *
 * @param enabled включает {@link ru.basnukaev.argumentmap.auth.web.security.RateLimitFilter}.
 *                По умолчанию {@code false}
 * @param loginAttemptsPerMinute лимит попыток /auth/login per IP в окне
 *                               1 минута. Default {@code 5}, валидный
 *                               диапазон 1..100
 * @param registerAttemptsPerMinute лимит попыток /auth/register per IP в
 *                                  окне 1 минута. Default {@code 3}
 *                                  (регистрация дороже login, ниже лимит).
 *                                  Валидный диапазон 1..100
 * @param lockoutDuration сколько IP остаётся заблокированным после
 *                        превышения лимита. Default {@code PT15M} -
 *                        15 минут (стандарт OWASP brute-force advisory).
 *                        Минимум 1 секунда, максимум 24 часа
 * @param whitelistedIps IP которые не подпадают под лимит. Применяется
 *                       к raw client IP (после X-Forwarded-For resolution).
 *                       Default {@code [127.0.0.1, ::1]} - localhost
 */
@ConfigurationProperties(prefix = "auth.rate-limit")
public record RateLimitProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("5") int loginAttemptsPerMinute,
        @DefaultValue("3") int registerAttemptsPerMinute,
        @DefaultValue("PT15M") Duration lockoutDuration,
        @DefaultValue({"127.0.0.1", "::1"}) List<String> whitelistedIps
) {

    public RateLimitProperties {
        if (loginAttemptsPerMinute < 1 || loginAttemptsPerMinute > 100) {
            throw new IllegalArgumentException(
                    "auth.rate-limit.login-attempts-per-minute=" + loginAttemptsPerMinute
                            + " вне диапазона 1..100");
        }
        if (registerAttemptsPerMinute < 1 || registerAttemptsPerMinute > 100) {
            throw new IllegalArgumentException(
                    "auth.rate-limit.register-attempts-per-minute=" + registerAttemptsPerMinute
                            + " вне диапазона 1..100");
        }
        if (lockoutDuration == null
                || lockoutDuration.isNegative()
                || lockoutDuration.isZero()
                || lockoutDuration.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException(
                    "auth.rate-limit.lockout-duration=" + lockoutDuration
                            + " должен быть в диапазоне 1s..24h");
        }
        // null-safe wrap - Spring binder может прислать null если не указан
        whitelistedIps = whitelistedIps == null ? List.of() : List.copyOf(whitelistedIps);
    }
}
