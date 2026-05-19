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
    /**
     * Bulk-операция удаления нескольких сущностей одним actor в одном
     * запросе. В отличие от N отдельных DELETE-записей, пишется один
     * audit row с массивом {@code entityIds} в changes JSON (см. backlog
     * 2026-05-19 «Bulk audit log consolidation»). entity_id row'а - null
     * (нет единого entity), parent_entity_id указывает на родителя
     * (topic для узлов).
     */
    public static final String BULK_DELETE = "BULK_DELETE";
    /**
     * Симметричный BULK_UPDATE для будущих bulk-status-change. Пока не
     * используется ни одним сайтом - зарезервирован.
     */
    public static final String BULK_UPDATE = "BULK_UPDATE";

    private AuditAction() {
    }

    public static boolean isValid(String action) {
        return CREATE.equals(action) || UPDATE.equals(action) || DELETE.equals(action)
                || VISIBILITY_CHANGE.equals(action)
                || MEMBER_ADD.equals(action) || MEMBER_REMOVE.equals(action)
                || MEMBER_ROLE_CHANGE.equals(action)
                || BULK_DELETE.equals(action) || BULK_UPDATE.equals(action);
    }
}
