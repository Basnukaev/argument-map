package ru.basnukaev.argumentmap.library.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело PATCH /api/v1/library/books/{id}/visibility (ADR-043 Amendment).
 */
public record UpdateBookVisibilityRequest(
        @NotNull
        @Pattern(regexp = "PRIVATE|SHARED|PUBLIC",
                message = "visibility должен быть PRIVATE, SHARED или PUBLIC")
        String visibility
) {
}
