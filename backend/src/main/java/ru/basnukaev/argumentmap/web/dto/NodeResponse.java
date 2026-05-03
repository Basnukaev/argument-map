package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

public record NodeResponse(
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
