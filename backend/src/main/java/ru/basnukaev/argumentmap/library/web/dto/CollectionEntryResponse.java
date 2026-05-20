package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись о книге в коллекции пользователя. Response DTO для
 * GET /api/v1/library/collections и POST.
 */
public record CollectionEntryResponse(
        UUID id,
        UUID bookId,
        String collectionName,
        Instant addedAt
) {
}
