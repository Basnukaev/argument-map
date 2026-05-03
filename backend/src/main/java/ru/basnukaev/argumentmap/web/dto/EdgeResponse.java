package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.EdgeType;

public record EdgeResponse(
        UUID id,
        UUID fromNodeId,
        UUID toNodeId,
        EdgeType edgeType,
        String rationale,
        UUID createdBy,
        Instant createdAt
) {
}
