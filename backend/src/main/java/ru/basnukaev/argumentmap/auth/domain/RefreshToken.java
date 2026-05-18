package ru.basnukaev.argumentmap.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись о выпущенном refresh-токене (ADR-047). raw JWT value НЕ хранится -
 * только SHA-256 hash. tokenHash - уникальный ключ для lookup на
 * {@code /auth/refresh}.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>issued - {@code revokedAt=null, replacedBy=null}
 *   <li>rotated - {@code revokedAt!=null, replacedBy={newTokenId},
 *       revocationReason="rotation"}
 *   <li>stolen-detected - {@code revokedAt!=null,
 *       revocationReason="stolen-detected"} - на всей chain user'а
 *   <li>logout - {@code revokedAt!=null, revocationReason="logout"}
 *   <li>expired - cleanup-janitor может revoke'нуть просроченные с
 *       reason="expired" (отложен, см. backlog)
 * </ul>
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedBy,
        String revocationReason
) {

    public static final String REASON_ROTATION = "rotation";
    public static final String REASON_STOLEN_DETECTED = "stolen-detected";
    public static final String REASON_LOGOUT = "logout";
    public static final String REASON_EXPIRED = "expired";
}
