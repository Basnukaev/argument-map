package ru.basnukaev.argumentmap.auth.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/v1/auth/me - текущий пользователь. Без password hash.
 */
public record MeResponse(
        UUID id,
        String username,
        String email,
        @Schema(allowableValues = {"USER", "STUDENT", "SCHOLAR", "ADMIN"})
        String role
) {
}
