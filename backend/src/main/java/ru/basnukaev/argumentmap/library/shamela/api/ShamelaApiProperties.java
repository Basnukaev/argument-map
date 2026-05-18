package ru.basnukaev.argumentmap.library.shamela.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация интеграции с shamela.ws desktop-API (ADR-020).
 *
 * <p>Все поля задаются в {@code application.yml} в блоке {@code shamela:},
 * биндятся через {@link ConfigurationProperties}. {@code api-key}
 * статический (публичный по природе - виден в mitmproxy-дампе любого
 * пользователя desktop-клиента), но через env-substitution
 * {@code SHAMELA_API_KEY} легко переключается без ребилда.
 */
@ConfigurationProperties(prefix = "shamela")
public record ShamelaApiProperties(
        String apiKey,
        String metadataHost,
        String filesHost,
        String downloadDir,
        int requestTimeoutSeconds,
        int connectTimeoutSeconds,
        /** Default https - prod scheme для shamela API. Override на http
         * нужен только в IT-stub тестах с локальным HttpServer (Сессия 39
         * code review fix). */
        String metadataScheme
) {
}
