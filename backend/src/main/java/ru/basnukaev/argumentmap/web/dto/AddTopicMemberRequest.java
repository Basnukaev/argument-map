package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело POST /api/v1/topics/{id}/members (ADR-043).
 */
public record AddTopicMemberRequest(
        @NotNull UUID userId,
        @NotNull
        @Pattern(regexp = "MEMBER|EDITOR",
                message = "role должен быть MEMBER или EDITOR")
        String role
) {
}
