package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда non-ADMIN user пытается обратиться к admin-only
 * endpoint (например {@code GET /api/v1/audit/admin}).
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-admin-only}. ADR-043 Amendment 3 (22.d).
 */
public class AdminOnlyException extends RuntimeException {

    private final UUID userId;

    public AdminOnlyException(UUID userId) {
        super("Доступ ограничен ролью ADMIN, текущий user " + userId);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
