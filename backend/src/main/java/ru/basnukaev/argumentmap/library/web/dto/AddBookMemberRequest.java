package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело POST /api/v1/library/books/{id}/members (ADR-043 Amendment).
 */
public record AddBookMemberRequest(
        @NotNull UUID userId,
        @NotNull
        @Pattern(regexp = "MEMBER|EDITOR",
                message = "role должен быть MEMBER или EDITOR")
        String role
) {
}
