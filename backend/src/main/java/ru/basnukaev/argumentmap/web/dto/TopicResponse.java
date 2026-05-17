package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TopicResponse(
        UUID id,
        String title,
        String description,
        UUID rootNodeId,
        UUID createdBy,
        Instant createdAt,
        // ADR-043: visibility per-entity. PRIVATE / SHARED / PUBLIC.
        String visibility,
        // агрегаты графа темы. На list-эндпоинте всегда заполнены, на остальных
        // могут быть нулём (метод toResponse(Topic) без счётчиков). См. ADR-016
        int nodeCount,
        int edgeCount
) {
}
