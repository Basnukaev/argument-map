package ru.basnukaev.argumentmap.library.storage;

import java.time.Instant;

/**
 * Метаданные объекта в bucket'е (результат {@code headObject}, ADR-024).
 * Не содержит content - только описание. Используется когда нужно
 * проверить размер / etag перед download'ом или validate существование.
 *
 * @param sizeBytes размер в байтах
 * @param contentType MIME (например {@code application/pdf})
 * @param etag S3 ETag
 * @param versionId текущая (latest) version если bucket versioned, иначе {@code null}
 * @param lastModified timestamp последнего изменения в S3
 */
public record StoredObject(
        long sizeBytes,
        String contentType,
        String etag,
        String versionId,
        Instant lastModified
) {
}
