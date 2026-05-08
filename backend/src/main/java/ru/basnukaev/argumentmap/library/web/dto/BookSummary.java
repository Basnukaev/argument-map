package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.library.domain.BookType;

public record BookSummary(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        Instant createdAt
) {
}
