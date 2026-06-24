package ru.basnukaev.argumentmap.hadith.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Конфиг cost-guard'а на AI-перевод матна (P2-3). Соответствует блоку
 * {@code hadith.translate.rate-limit:} в {@code application.yml}.
 * Используется {@link ru.basnukaev.argumentmap.hadith.service.MatnTranslateRateLimiter}.
 *
 * <p>Лимитирует число LLM-bound translate-запросов per пользователь в
 * sliding-window. Защищает от ситуации, когда залогиненный USER может
 * нагенерировать множество первых переводов и раскрутить платный AI-счёт
 * ({@code force=true}/регенерация уже ADMIN-only; первый перевод доступен
 * любому залогиненному — его и сторожим).
 *
 * <p>В отличие от {@code auth.rate-limit} (default {@code enabled=false},
 * opt-in в prod) — здесь default {@code enabled=true}: это cost-guard,
 * а не security-hardening, имеет смысл всегда. Отключается через env
 * {@code HADITH_TRANSLATE_RATE_LIMIT_ENABLED=false}.
 *
 * @param enabled включает лимит. По умолчанию {@code true}
 * @param requestsPerWindow сколько LLM-вызывающих translate-запросов на
 *                          одного пользователя разрешено в окне {@code window}.
 *                          Default {@code 20}, валидный диапазон 1..1000.
 *                          Считаются только запросы, реально идущие в LLM
 *                          (cached / 403 / 404 / 422 / 503 бюджет не тратят)
 * @param window размер sliding window. Default {@code PT1H} — 1 час.
 *               Должен быть положительным, максимум 24 часа
 */
@ConfigurationProperties(prefix = "hadith.translate.rate-limit")
public record MatnTranslateRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int requestsPerWindow,
        @DefaultValue("PT1H") Duration window
) {

    public MatnTranslateRateLimitProperties {
        if (requestsPerWindow < 1 || requestsPerWindow > 1000) {
            throw new IllegalArgumentException(
                    "hadith.translate.rate-limit.requests-per-window=" + requestsPerWindow
                            + " вне диапазона 1..1000");
        }
        if (window == null
                || window.isNegative()
                || window.isZero()
                || window.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException(
                    "hadith.translate.rate-limit.window=" + window
                            + " должен быть в диапазоне 1s..24h");
        }
    }
}
