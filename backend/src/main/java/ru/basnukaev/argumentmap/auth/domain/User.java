package ru.basnukaev.argumentmap.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Пользователь системы (ADR-040). password_hash nullable для legacy
 * X-User-Id flow - до Этапа 21.b существующие dev user'ы без hash
 * могут аутентифицироваться через X-User-Id фильтр.
 */
public record User(
        UUID id,
        String username,
        String email,
        String passwordHash,
        String role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
