package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Chapter(
        UUID id,
        UUID bookId,
        UUID parentChapterId,
        String title,
        int orderIndex,
        Integer startPageNumber,
        Instant createdAt
) {
}
