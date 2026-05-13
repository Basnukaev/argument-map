package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;

/**
 * Расширенный response с positional citation fields (ADR-027). Поле
 * {@code location} computed на бэкенде через SQL JOIN
 * (book.title + part + printedPage + range/bbox info).
 * {@code mode} derived из заполненности positional полей.
 * {@code bookId} продублирован из Source.bookId для удобства фронта
 * (deep link build не требует второго запроса).
 */
public record NodeSourceResponse(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        String location,
        CitationMode mode,
        UUID pageId,
        Integer rangeStart,
        Integer rangeEnd,
        UUID pdfFileId,
        Integer pdfPageNumber,
        JsonNode pdfBbox,
        UUID imageRegionId,
        UUID bookId,
        Instant createdAt
) {
}
