package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Тема (корневая сущность графа аргументации). Visibility - ADR-043:
 * PRIVATE (только owner), SHARED (owner + members), PUBLIC (все
 * аутентифицированные могут read). ADMIN bypass'ит в Service-слое.
 */
public record Topic(
        UUID id,
        String title,
        String description,
        UUID rootNodeId,
        UUID createdBy,
        Instant createdAt,
        String visibility
) {
}
