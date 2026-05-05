package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.EdgeType;

public record CreateEdgeRequest(
        @NotNull UUID fromNodeId,
        @NotNull UUID toNodeId,
        @NotNull EdgeType edgeType,
        @Size(max = 2000) String rationale,
        @Size(max = 20) String sourceHandle,
        @Size(max = 20) String targetHandle
) {
}
