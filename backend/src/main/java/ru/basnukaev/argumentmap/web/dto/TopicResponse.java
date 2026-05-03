package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TopicResponse(
        UUID id,
        String title,
        String description,
        UUID rootNodeId,
        UUID createdBy,
        Instant createdAt
) {
}
