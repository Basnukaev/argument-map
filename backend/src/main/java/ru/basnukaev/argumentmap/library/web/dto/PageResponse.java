package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code formattedContent} - ProseMirror JSON (Tiptap output, ADR-039,
 * миграция 33). NULL для legacy-страниц без editor session. Передаётся
 * как Jackson {@link JsonNode}, чтобы фронт получал structured JSON
 * (а не строку), и оригинальная вложенность сохранялась без двойной
 * сериализации.
 */
public record PageResponse(
        UUID id,
        UUID bookId,
        UUID chapterId,
        int pageNumber,
        String printedPage,
        String part,
        Integer pdfPageNumber,
        String textContent,
        String imageUrl,
        JsonNode formattedContent,
        List<ImageRegionResponse> imageRegions,
        Instant createdAt,
        Instant updatedAt
) {
}
