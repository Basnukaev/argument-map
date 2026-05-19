package ru.basnukaev.argumentmap.auth.web.security;

import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Helper для применения HTTP security headers к {@link HttpSecurity}.
 * Используется обоими chain'ами - main {@link SecurityConfig} и
 * {@link ActuatorSecurityConfig} - чтобы CSP / HSTS / Referrer / Permissions
 * policy задавались в одном месте.
 *
 * <p>Без этого helper'а изменение CSP / HSTS требует правки в двух местах
 * с риском разъехавшихся policy и разных header'ов на одинаковых эндпоинтах.
 */
public final class SecurityHeadersCustomizer {

    private SecurityHeadersCustomizer() {}

    /**
     * Применяет shared HTTP security headers. В prod profile добавляет
     * HSTS + CSP, в dev/test/local - только Referrer-Policy +
     * Permissions-Policy (HSTS на http:// игнорируется браузером и
     * только spam'ит DevTools console; CSP в dev мешает Vite HMR с
     * inline style + ws://localhost socket).
     *
     * <p>Spring Security 6 defaults (X-Content-Type-Options=nosniff,
     * X-Frame-Options=DENY, Cache-Control) применяются автоматически через
     * {@code headers()} builder defaults - не требуется явный opt-in.
     *
     * @param http builder для применения headers
     * @param prodProfile true для prod chain - добавит HSTS + CSP
     */
    public static void apply(HttpSecurity http, boolean prodProfile) throws Exception {
        http.headers(headers -> {
            // Referrer-Policy `strict-origin-when-cross-origin` - safer
            // дефолт чем full Referer leak (browser default различается)
            headers.referrerPolicy(rp -> rp.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
            // Permissions-Policy ограничивает доступ к sensor API
            // которые SPA не использует
            headers.permissionsPolicyHeader(pp -> pp.policy(
                    "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()"));
            if (prodProfile) {
                // HSTS только в prod (HTTPS-only deploy) - в dev mixed-content
                // с :9090 без TLS, header игнорируется браузером но spam'ит
                // DevTools console
                headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(false)
                        .maxAgeInSeconds(31_536_000L)); // 1 год
                // CSP минимальный strict policy для prod. SPA не использует
                // eval (Vite production bundle - static), inline-style
                // нужен Tailwind v4 (CSS variables), connect-src - наш
                // backend. img-src data: для PDF page previews
                headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                                + "script-src 'self'; "
                                + "style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data: blob:; "
                                + "font-src 'self' data:; "
                                + "connect-src 'self'; "
                                + "frame-ancestors 'none'; "
                                + "base-uri 'self'; "
                                + "form-action 'self'"));
            }
        });
    }

    /**
     * Консистентный prod-profile detection. Активный profile должен
     * содержать литерал {@code "prod"} (case-sensitive, как Spring profile
     * names обычно).
     *
     * <p>Введён чтобы main {@link SecurityConfig} и
     * {@link ActuatorSecurityConfig} не использовали разные правила
     * detection (раньше main расширял dev/test/local на «empty profile =
     * local», actuator - только {@code "prod"}). Семантика результата
     * сохранена через флаг {@code prodProfile = isProdProfile(env)} +
     * вызывающий код решает что делать с другими profile'ами (main
     * проверяет {@code devOrTestProfile}, actuator - {@code prodProfile}).
     */
    public static boolean isProdProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equals);
    }
}
