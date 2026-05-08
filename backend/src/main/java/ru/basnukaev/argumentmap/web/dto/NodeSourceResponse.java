package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NodeSourceResponse(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        String location,
        Instant createdAt
) {
}
