package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        List<ImageRegionResponse> imageRegions,
        Instant createdAt,
        Instant updatedAt
) {
}
