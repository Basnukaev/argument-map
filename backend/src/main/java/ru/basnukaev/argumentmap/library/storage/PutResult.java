package ru.basnukaev.argumentmap.library.storage;

/**
 * Результат успешного {@code put} в object storage (ADR-024).
 *
 * @param contentHash SHA-256 hex 64 символа - вычисляется на стороне бэка
 *                    из bytes файла. Записывается в {@code library_files.content_hash}
 * @param etag S3 ETag из response. Для simple upload это MD5 от content,
 *             для multipart - composite hash. Не используется для security,
 *             но полезен для conditional re-fetch
 * @param sizeBytes реальный размер записанных bytes
 * @param versionId S3 version ID если bucket versioned, иначе {@code null}.
 *                  Для critical bucket'ов (versioning ON) - non-null, для
 *                  {@code derived-artifacts} - null
 */
public record PutResult(
        String contentHash,
        String etag,
        long sizeBytes,
        String versionId
) {
}
