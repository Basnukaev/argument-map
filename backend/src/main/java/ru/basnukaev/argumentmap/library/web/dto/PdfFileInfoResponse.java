package ru.basnukaev.argumentmap.library.web.dto;

/**
 * Один PDF-файл из multi-volume книги. {@code label} - человекочитаемая
 * метка ("المقدمة" или "Том 1") из shamela-формата
 * {@code "filename|label"}.
 *
 * <p>{@code sizeBytes} и {@code pageCount} - nullable, будут заполняться
 * когда добавим metadata-prefetch.
 *
 * <p>{@code filename} НЕ возвращается клиенту - чтобы frontend не
 * мог собрать прямую ссылку на archive.org/shamela CDN в обход
 * нашего endpoint (audit, rate limiting, кеш будут работать только
 * если все запросы идут через бэк).
 */
public record PdfFileInfoResponse(
        int index,
        String label,
        Long sizeBytes,
        Integer pageCount
) {
}
