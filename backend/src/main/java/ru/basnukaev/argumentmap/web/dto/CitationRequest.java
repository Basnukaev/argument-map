package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

import ru.basnukaev.argumentmap.domain.PdfBbox;

/**
 * Запрос на создание positional citation. Ровно один из трёх режимов:
 * <ul>
 *   <li>TEXT - {@code pageId} + {@code rangeStart} + {@code rangeEnd}</li>
 *   <li>PDF - {@code pdfFileId} + {@code pdfPageNumber} + {@code pdfBbox}</li>
 *   <li>REGION - {@code imageRegionId} (future)</li>
 * </ul>
 * Mode validation на сервисе + дублируется CHECK constraint на БД.
 */
public record CitationRequest(
        @NotNull UUID bookId,
        // TEXT mode
        UUID pageId,
        Integer rangeStart,
        Integer rangeEnd,
        // PDF mode
        UUID pdfFileId,
        Integer pdfPageNumber,
        PdfBbox pdfBbox,
        // REGION mode
        UUID imageRegionId,
        // common
        @Size(max = 10000) String quote,
        @Size(max = 2000) String context
) {
}
