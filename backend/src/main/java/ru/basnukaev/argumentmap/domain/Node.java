package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Node(
        UUID id,
        UUID topicId,
        NodeType nodeType,
        String content,
        NodeStatus status,
        int weight,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
