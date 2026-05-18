package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;

/**
 * Тело POST /api/v1/sources/{sourceId}/grades. Все 4 поля кроме scholar
 * и grade опциональные (citation/comment).
 */
public record CreateHadithGradeRequest(
        @NotNull UUID scholarId,
        @NotNull HadithGradeValue grade,
        @Size(max = 500) String gradeCitation,
        @Size(max = 5000) String comment
) {
}
