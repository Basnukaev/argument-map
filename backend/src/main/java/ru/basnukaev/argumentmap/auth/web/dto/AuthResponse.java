package ru.basnukaev.argumentmap.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

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
            String role
    ) {
    }
}
