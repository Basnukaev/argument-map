package ru.basnukaev.argumentmap.auth.domain;

import java.util.List;

/**
 * Роли пользователей. Vision 49d Section 2.4 расширил с 2 до 4 ролей.
 * Используются String-литералы (не enum) - совместимы с CHECK constraint
 * в БД, без JDBC marshalling. Хранение в `users.role` VARCHAR(20).
 *
 * <p>Иерархия монотонная (USER &lt; STUDENT &lt; SCHOLAR &lt; ADMIN) -
 * каждая высшая роль наследует возможности всех нижних. `hasAtLeast`
 * helper позволяет писать `if (hasAtLeast(actual, SCHOLAR))` вместо
 * перечисления N if-веток.
 *
 * <p>Семантика:
 * <ul>
 *   <li>USER (default) - read + voting</li>
 *   <li>STUDENT - + comments + Q&amp;A answer authorship</li>
 *   <li>SCHOLAR - + hadith grades, оценки иснадов</li>
 *   <li>ADMIN - + admin pages, role management, bypass all permission checks</li>
 * </ul>
 *
 * <p>Per-entity ACL (TopicMember/BookMember с ролями MEMBER/EDITOR) -
 * отдельная axis от global user.role. См. ADR-043.
 */
public final class UserRole {

    public static final String USER = "USER";
    public static final String STUDENT = "STUDENT";
    public static final String SCHOLAR = "SCHOLAR";
    public static final String ADMIN = "ADMIN";

    /** Список всех valid ролей в порядке возрастания привилегий. */
    public static final List<String> ALL = List.of(USER, STUDENT, SCHOLAR, ADMIN);

    private UserRole() {
    }

    public static boolean isValid(String role) {
        if (role == null) return false;
        return ALL.contains(role);
    }

    /**
     * Возвращает true если actual роль не ниже required в иерархии.
     * null actual обрабатывается как USER (lowest) - safe default для
     * anonymous / dev-test fallback.
     */
    public static boolean hasAtLeast(String actual, String required) {
        if (required == null || !isValid(required)) {
            throw new IllegalArgumentException("required role must be one of " + ALL + ", got: " + required);
        }
        if (actual == null) return false;
        int actualRank = ALL.indexOf(actual);
        int requiredRank = ALL.indexOf(required);
        if (actualRank == -1) return false;
        return actualRank >= requiredRank;
    }
}
