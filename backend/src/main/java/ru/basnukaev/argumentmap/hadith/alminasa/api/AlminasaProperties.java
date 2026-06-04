package ru.basnukaev.argumentmap.hadith.alminasa.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация доступа к открытому ES-прокси alminasa.ai (ADR-060, спека §A).
 *
 * <p>{@code origin} отдельно от {@code baseUrl}: прокси проверяет только
 * заголовки Origin/Referer (HAR-анализ) — в IT base-url подменяется на
 * локальный stub, а Origin продолжает указывать на alminasa.ai.
 *
 * <p>{@code httpProxy} — опциональный корп-прокси {@code http://[user:pass@]host:port},
 * вешается ТОЛЬКО на alminasa-HttpClient (не глобально). Аутентификация через
 * {@link java.net.Authenticator} безопасна: alminasa не требует серверного
 * Authorization-заголовка (ср. gotcha «LLM за корп-прокси», где Authenticator
 * вырезал Bearer).
 */
@ConfigurationProperties(prefix = "alminasa")
public record AlminasaProperties(
        boolean enabled,
        String baseUrl,
        String origin,
        String indexPrefix,
        String indexSuffix,
        String httpProxy,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        Crawl crawl
) {

    public record Crawl(
            /** Размер страницы hadith-12 (search_after). */
            int pageSize,
            /** Пауза между страницами, мс — консервативный rate-limit (спека §G). */
            long delayMs,
            /** Сколько hadith_id в одном terms-запросе зависимых индексов. */
            int dependentBatchSize,
            /** size для terms-ответов rulings/explanations (warn при переполнении). */
            int dependentFetchSize,
            /** RUNNING-claim старше этого — считается мёртвым (перехват, как ai.edit). */
            int staleTimeoutMinutes
    ) {
    }
}
