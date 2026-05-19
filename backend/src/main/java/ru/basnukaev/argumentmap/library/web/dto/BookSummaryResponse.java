package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.library.domain.BookType;

public record BookSummaryResponse(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        UUID createdBy,
        Instant createdAt,
        String visibility
) {
}
