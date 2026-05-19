package ru.basnukaev.argumentmap.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ register/login/refresh (ADR-040). Содержит access token и
 * информацию о пользователе. Refresh token уходит в HttpOnly Cookie,
 * не в body - защита от XSS.
 */
public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String username,
            String email,
            @Schema(allowableValues = {"USER", "ADMIN"})
            String role
    ) {
    }
}
