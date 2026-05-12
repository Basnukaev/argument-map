package ru.basnukaev.argumentmap.library.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфиг object storage слоя (ADR-024). Соответствует блоку {@code storage:}
 * в {@code application.yml}. Используется {@link S3ClientConfig} для
 * построения {@code S3Client} bean и {@code ObjectStorageService} (25.b.4)
 * для bucket-naming.
 *
 * @param endpoint S3 endpoint URL. По умолчанию MinIO в docker-compose.
 *                 В проде - URL S3 провайдера (R2/B2/Yandex/AWS)
 * @param region AWS region. Обязателен для SDK v2 даже на MinIO (валидация
 *               при build). По умолчанию {@code us-east-1}
 * @param accessKey, secretKey credentials. Для MinIO dev - {@code minioadmin}
 * @param pathStyleAccess true для path-style URL ({@code host/bucket/key})
 *                       вместо virtual-hosted ({@code bucket.host/key}).
 *                       Обязателен для MinIO, безопасен для AWS S3
 * @param connectTimeout timeout установки соединения
 * @param readTimeout timeout чтения response (важен для крупных PDF stream)
 * @param maxRetries retries на transient errors. AWS SDK v2 встроенный
 *                   exponential backoff
 */
@ConfigurationProperties(prefix = "storage")
public record ObjectStorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        Buckets buckets
) {

    /**
     * Имена bucket'ов для 4 categorii файлов (ADR-024). Разделение по
     * операционной семантике (criticality + retention), не по content type.
     *
     * @param importedBooks shamela / archive.org PDF, EPUB - re-derivable
     * @param userUploads user PDF/EPUB (Этап 16), Q&A attachments - critical
     * @param pageImages image-сканы рукописей (Этап 17) - critical
     * @param derived PDF previews, AI artefacts, exports - re-derivable, TTL ok
     */
    public record Buckets(
            String importedBooks,
            String userUploads,
            String pageImages,
            String derived
    ) {
    }
}
