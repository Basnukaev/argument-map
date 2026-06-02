package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import ru.basnukaev.argumentmap.library.domain.BookContentKind;
import ru.basnukaev.argumentmap.library.domain.BookType;

public record BookResponse(
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
        String visibility,
        // Ссылка на обложку (миграция 67, ADR-056) - nullable. Фронт
        // рендерит её на карточке книги; null → letter-avatar fallback.
        String coverUrl,
        // Availability-классификация (миграция 69) - ортогональна bookType
        // (жанр). TEXT_ONLY/TEXT_AND_FILE/FILE_ONLY. Фронт по ней решает
        // какой режим reader открыть (текст / PDF).
        BookContentKind contentKind
) {
}
