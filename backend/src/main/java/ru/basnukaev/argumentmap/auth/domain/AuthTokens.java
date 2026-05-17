package ru.basnukaev.argumentmap.auth.domain;

import java.time.Instant;

/**
 * Пара выпущенных токенов (ADR-040). Access - короткоживущий, refresh -
 * длинноживущий. expiresIn в секундах для совместимости с OAuth2 token
 * response convention.
 */
public record AuthTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
