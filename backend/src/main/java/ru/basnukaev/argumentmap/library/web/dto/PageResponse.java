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
 *
 * <p>{@code imageBucket}/{@code imageStorageKey}/{@code imageUploadedAt} -
 * pointer на uploaded скан страницы в MinIO (Этап 17.a, ADR-041, миграция 34).
 * Заполнены вместе - страница имеет image после {@code PageImageService.upload}.
 * Все NULL - text-only page (shamela ETL или PDF text extraction).
 *
 * <p>{@code ocrStatus} - state machine OCR pipeline (ADR-041):
 * {@code PENDING}/{@code PROCESSING}/{@code DONE}/{@code FAILED} либо
 * NULL если OCR не применим (нет image scan). Frontend ImagePageRenderer
 * (18.e) использует для отображения busy state на странице.
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
        String imageBucket,
        String imageStorageKey,
        Instant imageUploadedAt,
        String ocrStatus,
        Instant ocrStartedAt,
        Instant ocrCompletedAt,
        List<ImageRegionResponse> imageRegions,
        Instant createdAt,
        Instant updatedAt
) {
}
