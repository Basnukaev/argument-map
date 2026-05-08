package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

public record Book(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        String description,
        String metadata,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
