package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

public record Edge(
        UUID id,
        UUID fromNodeId,
        UUID toNodeId,
        EdgeType edgeType,
        String rationale,
        String sourceHandle,
        String targetHandle,
        UUID createdBy,
        Instant createdAt,
        int zIndex
) {
}
