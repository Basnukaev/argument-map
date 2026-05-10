package ru.basnukaev.argumentmap.library.web.dto;

import java.util.List;

/**
 * Метаданные PDF-источника для книги. Возвращается из
 * {@code GET /api/v1/library/books/{bookId}/pdf/info}.
 * Frontend использует {@code files} для построения dropdown
 * селектора томов если книга multi-volume, или скрывает выбор если
 * один файл.
 */
public record PdfInfoResponse(
        boolean hasCover,
        Long totalSizeBytes,
        List<PdfFileInfoResponse> files
) {
}
