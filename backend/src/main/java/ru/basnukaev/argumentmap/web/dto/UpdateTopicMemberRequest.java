package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело PATCH /api/v1/topics/{id}/members/{memberId} (ADR-043).
 * Сейчас единственное менимое поле - role.
 */
public record UpdateTopicMemberRequest(
        @NotNull
        @Pattern(regexp = "MEMBER|EDITOR",
                message = "role должен быть MEMBER или EDITOR")
        String role
) {
}
