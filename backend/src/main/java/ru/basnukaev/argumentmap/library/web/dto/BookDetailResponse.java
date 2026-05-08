package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.basnukaev.argumentmap.library.domain.BookType;

public record BookDetailResponse(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        String description,
        JsonNode metadata,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<ChapterResponse> chapters
) {
}
