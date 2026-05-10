package ru.basnukaev.argumentmap.library.pdf.service;

import java.util.UUID;

/**
 * Книга не имеет привязанного PDF-источника (нет provider'а который
 * её поддерживает). Маппится в HTTP 404 в
 * {@code GlobalExceptionHandler}.
 */
public class PdfNotAvailableException extends RuntimeException {

    public PdfNotAvailableException(UUID bookId) {
        super("Для книги " + bookId + " не привязан PDF-источник");
    }

    public PdfNotAvailableException(UUID bookId, int fileIndex, int totalFiles) {
        super("Для книги " + bookId + " fileIndex=" + fileIndex
                + " вне диапазона (всего файлов: " + totalFiles + ")");
    }
}
