package ru.basnukaev.argumentmap.web.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Запрос группового удаления узлов (DELETE /api/v1/nodes/bulk).
 *
 * <p>Backlog «Bulk audit log consolidation» - вместо N отдельных
 * {@code DELETE /api/v1/nodes/{id}} запросов (каждый пишет свой
 * audit row) - один endpoint, один audit row с массивом entityIds.
 *
 * <p>Лимит {@code MAX_BULK_SIZE=100} - защита от случайной DoS-нагрузки
 * (transaction на 10k узлов + status recalc на тему), достаточно для
 * UI «выделил всё в большой теме». При выборе >100 - frontend chunk'ует
 * либо просим увеличить лимит после реального use-case.
 */
public record BulkDeleteNodesRequest(
        @NotNull
        @NotEmpty
        @Size(min = 1, max = 100)
        List<UUID> nodeIds
) {
}
