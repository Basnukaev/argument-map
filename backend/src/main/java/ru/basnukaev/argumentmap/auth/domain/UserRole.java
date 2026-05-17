package ru.basnukaev.argumentmap.auth.domain;

/**
 * Роли пользователей (ADR-040). MVP - две роли. Перешли бы на enum,
 * но String-литералы соответствуют CHECK constraint в таблице и не
 * требуют JDBC-маппинга. RBAC permissions per-entity - Этап 22.
 */
public final class UserRole {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";

    private UserRole() {
    }

    public static boolean isValid(String role) {
        return USER.equals(role) || ADMIN.equals(role);
    }
}
