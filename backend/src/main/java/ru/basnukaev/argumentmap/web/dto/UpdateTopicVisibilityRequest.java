package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело PATCH /api/v1/topics/{id}/visibility (ADR-043).
 */
public record UpdateTopicVisibilityRequest(
        @NotNull
        @Pattern(regexp = "PRIVATE|SHARED|PUBLIC",
                message = "visibility должен быть PRIVATE, SHARED или PUBLIC")
        String visibility
) {
}
