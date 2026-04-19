package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Revision(
        UUID id,
        UUID nodeId,
        String contentBefore,
        String contentAfter,
        UUID changedBy,
        Instant changedAt
) {
}
