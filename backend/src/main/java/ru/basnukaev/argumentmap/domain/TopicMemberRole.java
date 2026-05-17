package ru.basnukaev.argumentmap.domain;

/**
 * Роли членов SHARED-темы (ADR-043).
 *
 * <p>MEMBER - только чтение, EDITOR - чтение + запись (создание/
 * обновление узлов и рёбер, но НЕ удаление самой темы - это только owner).
 */
public final class TopicMemberRole {

    public static final String MEMBER = "MEMBER";
    public static final String EDITOR = "EDITOR";

    private TopicMemberRole() {
    }

    public static boolean isValid(String value) {
        return MEMBER.equals(value) || EDITOR.equals(value);
    }
}
