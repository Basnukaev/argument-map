package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда у пользователя недостаточно роли для выполнения
 * операции (например USER пытается добавить hadith grade требующий
 * SCHOLAR). Vision 49d Section 2.4 / spec
 * {@code docs/superpowers/specs/2026-05-20-roles-system-design.md}.
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-insufficient-role} + details
 * {@code {currentRole, requiredRole}}.
 *
 * <p>Отдельный exception от {@link AdminOnlyException} - последний
 * остаётся для cases где требуется именно ADMIN (audit/admin,
 * change-role). InsufficientRoleException - general case для
 * иерархического сравнения.
 */
public class InsufficientRoleException extends RuntimeException {

    private final UUID userId;
    private final String currentRole;
    private final String requiredRole;

    public InsufficientRoleException(UUID userId, String currentRole, String requiredRole) {
        super("User " + userId + " has role " + currentRole + ", required: " + requiredRole);
        this.userId = userId;
        this.currentRole = currentRole;
        this.requiredRole = requiredRole;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public String getRequiredRole() {
        return requiredRole;
    }
}
