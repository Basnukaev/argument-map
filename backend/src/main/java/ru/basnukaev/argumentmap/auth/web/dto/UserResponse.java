package ru.basnukaev.argumentmap.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public user info (без password_hash). Используется в admin
 * endpoints user management (GET /users, PATCH /users/{id}/role).
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        @Schema(allowableValues = {"USER", "STUDENT", "SCHOLAR", "ADMIN"})
        String role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
