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
        // ADR-044: алгоритм пересчёта статусов узлов. MVP / DUNG_GROUNDED.
        String statusAlgorithm,
        // агрегаты графа темы. На list-эндпоинте всегда заполнены, на остальных
        // могут быть нулём (метод toResponse(Topic) без счётчиков). См. ADR-016
        int nodeCount,
        int edgeCount,
        // голосование за темы (community-сигнал популярности, ADR-053).
        // voteScore = upvotes - downvotes (нетто, может быть
        // отрицательным). На list/detail заполнены через bulk-load из
        // topic_votes; на mutating endpoint'ах могут быть default 0/null.
        // userVote ∈ {-1, +1, null} - голос вызывающего user'а (null = не голосовал)
        int voteScore,
        Integer userVote
) {
}
