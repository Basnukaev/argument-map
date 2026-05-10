package ru.basnukaev.argumentmap.library.pdf.domain;

/**
 * Метаданные одного PDF-файла из multi-volume книги.
 *
 * <p>Книги в shamela могут быть разбиты на несколько PDF: главный
 * том + предисловие + приложения. {@code label} приходит из shamela
 * формата {@code "filename|метка"} (например
 * {@code "01_113015p.pdf|المقدمة"}). Если pipe отсутствует - label
 * берётся из filename без расширения.
 *
 * <p>{@code sizeBytes} и {@code pageCount} - nullable на MVP. Будут
 * заполняться когда добавим metadata-prefetch (HEAD-запрос) или
 * PDF.js page count parsing.
 */
public record PdfFileInfo(
        int index,
        String filename,
        String label,
        Long sizeBytes,
        Integer pageCount
) {
}
