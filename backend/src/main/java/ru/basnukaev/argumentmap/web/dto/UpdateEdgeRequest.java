package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.EdgeType;

/**
 * PATCH /api/v1/edges/{id}. Все поля опциональные - переданные применяются,
 * отсутствующие сохраняют текущее значение. Финальное состояние ребра
 * валидируется заново (selfloop, граница темы, матрица ADR-010). Если
 * валидация не проходит - ребро остаётся в исходном виде, никаких
 * частичных изменений.
 */
public record UpdateEdgeRequest(
        UUID fromNodeId,
        UUID toNodeId,
        EdgeType edgeType,
        @Size(max = 2000) String rationale,
        @Size(max = 20) String sourceHandle,
        @Size(max = 20) String targetHandle
) {
}
