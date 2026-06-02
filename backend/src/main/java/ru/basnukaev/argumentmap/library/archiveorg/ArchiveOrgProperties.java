package ru.basnukaev.argumentmap.library.archiveorg;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация интеграции с archive.org (ADR-056). Биндится из
 * блока {@code archiveorg:} в {@code application.yml}.
 *
 * <p>{@code baseUrl} вынесен в property чтобы IT-тесты могли указать
 * на локальный HttpServer-stub (тот же приём что
 * {@code shamela.metadataScheme=http}). Дефолт - публичный
 * {@code https://archive.org}, auth не требуется.
 *
 * <p>{@code requestTimeoutSeconds} - таймаут на metadata-вызов
 * (JSON ~20-30КБ, быстрый). Скачивание PDF идёт через отдельный
 * {@code PdfFetcher} с собственным таймаутом.
 */
@ConfigurationProperties(prefix = "archiveorg")
public record ArchiveOrgProperties(
        String baseUrl,
        int requestTimeoutSeconds,
        int connectTimeoutSeconds
) {

    public ArchiveOrgProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://archive.org";
        }
        // strip trailing slash - конкатенация URL-сегментов делается явно
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 30;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
