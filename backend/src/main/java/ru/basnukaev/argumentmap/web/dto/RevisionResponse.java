package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

public record RevisionResponse(
        UUID id,
        UUID nodeId,
        String contentBefore,
        String contentAfter,
        UUID changedBy,
        Instant changedAt
) {
}
