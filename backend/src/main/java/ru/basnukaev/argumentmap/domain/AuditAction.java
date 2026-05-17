package ru.basnukaev.argumentmap.domain;

/**
 * Константы action для audit_log (Этап 22.d, ADR-043 Amendment 3).
 * String-литералы (не enum) - тот же подход что у {@link AuditEntityType}.
 */
public final class AuditAction {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String VISIBILITY_CHANGE = "VISIBILITY_CHANGE";
    public static final String MEMBER_ADD = "MEMBER_ADD";
    public static final String MEMBER_REMOVE = "MEMBER_REMOVE";
    public static final String MEMBER_ROLE_CHANGE = "MEMBER_ROLE_CHANGE";

    private AuditAction() {
    }

    public static boolean isValid(String action) {
        return CREATE.equals(action) || UPDATE.equals(action) || DELETE.equals(action)
                || VISIBILITY_CHANGE.equals(action)
                || MEMBER_ADD.equals(action) || MEMBER_REMOVE.equals(action)
                || MEMBER_ROLE_CHANGE.equals(action);
    }
}
