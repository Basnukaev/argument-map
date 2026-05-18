package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;

/**
 * Тело PATCH /api/v1/sources/grades/{gradeId}. grade обязателен (это
 * primary поле). citation/comment - replace-semantics (передать null
 * для очистки).
 */
public record UpdateHadithGradeRequest(
        @NotNull HadithGradeValue grade,
        @Size(max = 500) String gradeCitation,
        @Size(max = 5000) String comment
) {
}
