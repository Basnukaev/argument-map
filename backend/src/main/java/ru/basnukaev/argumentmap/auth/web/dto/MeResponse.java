package ru.basnukaev.argumentmap.auth.web.dto;

import java.util.UUID;

/**
 * GET /api/v1/auth/me - текущий пользователь. Без password hash.
 */
public record MeResponse(
        UUID id,
        String username,
        String email,
        String role
) {
}
