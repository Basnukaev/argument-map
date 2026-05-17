package ru.basnukaev.argumentmap.library.imports.web;

import java.util.UUID;

/**
 * Ответ на {@code POST /api/v1/library/imports/file} (Этап 16.b).
 * Краткая сводка успешного import'а - frontend дальше делает
 * {@code GET /api/v1/library/books/{bookId}} для полных данных книги.
 *
 * @param bookId UUID созданной книги
 * @param fileId UUID записи в {@code library_files} catalog (для
 *               будущих admin-операций над blob'ом)
 * @param pageCount число phys-страниц PDF = число созданных
 *                  {@code lib_pages} rows
 * @param contentHash SHA-256 hex 64 - integrity hash blob'а
 * @param sizeBytes реальный размер uploaded PDF
 * @param bucket имя bucket'а в S3-storage где лежит blob
 * @param storageKey ключ blob'а внутри bucket'а
 */
public record FileImportResponse(
        UUID bookId,
        UUID fileId,
        int pageCount,
        String contentHash,
        long sizeBytes,
        String bucket,
        String storageKey
) {
}
