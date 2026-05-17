package ru.basnukaev.argumentmap.auth.domain;

import java.util.UUID;

/**
 * Principal внутри SecurityContext (ADR-040). Минимальная проекция
 * User без password hash - читается из JWT claims или X-User-Id
 * fallback. {@code CurrentUserArgumentResolver} вытаскивает {@code id}.
 */
public record AuthenticatedUser(
        UUID id,
        String username,
        String email,
        String role
) {
}
