package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Page(
        UUID id,
        UUID bookId,
        UUID chapterId,
        int pageNumber,
        String textContent,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
