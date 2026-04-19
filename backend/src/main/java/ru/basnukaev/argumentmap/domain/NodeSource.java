package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record NodeSource(
        UUID nodeId,
        UUID sourceId,
        String quote,
        String context,
        Instant createdAt
) {
}
