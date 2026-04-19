package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Topic(
        UUID id,
        String title,
        String description,
        UUID rootNodeId,
        UUID createdBy,
        Instant createdAt
) {
}
