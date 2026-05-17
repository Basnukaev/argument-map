package ru.basnukaev.argumentmap.library.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело PATCH /api/v1/library/books/{id}/members/{memberId} (ADR-043 Amendment).
 */
public record UpdateBookMemberRequest(
        @NotNull
        @Pattern(regexp = "MEMBER|EDITOR",
                message = "role должен быть MEMBER или EDITOR")
        String role
) {
}
