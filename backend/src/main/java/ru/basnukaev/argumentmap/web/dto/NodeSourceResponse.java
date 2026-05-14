package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;

/**
 * Response с structured citation (ADR-028). Поля location, bookId, pageId,
 * rangeStart, rangeEnd, pdfFileId, pdfPageNumber, pdfBbox, imageRegionId
 * старого формата заменены на nested CitationResponse - frontend рендерит
 * каждое поле в своём блоке для правильного RTL/naskh.
 */
public record NodeSourceResponse(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        CitationMode mode,
        CitationResponse citation,
        Instant createdAt
) {
}
