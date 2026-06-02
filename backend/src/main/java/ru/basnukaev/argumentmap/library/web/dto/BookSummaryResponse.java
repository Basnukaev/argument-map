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
        String visibility,
        // Ссылка на обложку (миграция 67, ADR-056) - nullable. Карточка
        // книги в списке рендерит её; null → letter-avatar fallback.
        String coverUrl
) {
}
