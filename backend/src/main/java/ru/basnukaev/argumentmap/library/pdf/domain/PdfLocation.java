package ru.basnukaev.argumentmap.library.pdf.domain;

/**
 * Resolved-ссылка на PDF файл в object storage (ADR-024, 25.b.6).
 * Возвращается из {@code PdfSourceProvider.locateFile} - содержит
 * всю информацию необходимую для streaming через
 * {@code ObjectStorageService.get} / {@code getRange}.
 *
 * @param bucket S3 bucket name (например {@code library-imported-books})
 * @param storageKey ключ объекта в bucket'е (например
 *                   {@code {bookId}/01_book.pdf})
 * @param sizeBytes размер PDF файла в байтах - нужен для HTTP Content-Length
 *                  + Range header validation на уровне контроллера
 * @param contentType MIME type ({@code application/pdf} стандартно)
 */
public record PdfLocation(
        String bucket,
        String storageKey,
        long sizeBytes,
        String contentType
) {
}
