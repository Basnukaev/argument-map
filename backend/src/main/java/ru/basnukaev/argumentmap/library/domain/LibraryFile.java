package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись в Postgres catalog объектов в S3-compatible object storage
 * (ADR-024). Один объект в bucket'е - одна запись. Source of truth о
 * физических blob'ах: где лежит ({@code bucket}+{@code storageKey}),
 * откуда пришёл ({@code sourceUrl}+{@code sourceType}), integrity
 * ({@code contentHash} SHA-256), soft-delete state ({@code deletedAt}).
 *
 * @param bookId nullable - derived-artifacts могут не быть привязаны к
 *               конкретной книге
 * @param sourceUrl nullable - user-uploaded и derived не имеют upstream URL
 * @param etag nullable - не все upstream-провайдеры отдают ETag header
 * @param shamelaMajorRelease nullable - заполняется только для
 *                            {@code SHAMELA} source-type
 * @param lastVerifiedAt nullable - timestamp последней integrity-check
 *                       (background job)
 * @param deletedAt nullable - {@code NULL} = active, иначе soft-deleted
 */
public record LibraryFile(
        UUID fileId,
        UUID bookId,
        String bucket,
        String storageKey,
        String sourceUrl,
        LibraryFileSourceType sourceType,
        String contentHash,
        long sizeBytes,
        String etag,
        Instant downloadedAt,
        Instant lastVerifiedAt,
        Integer shamelaMajorRelease,
        String metadata,
        Instant deletedAt
) {
}
