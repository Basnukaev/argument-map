package ru.basnukaev.argumentmap.library.domain;

/**
 * Роли членов SHARED-книги (ADR-043 Amendment, Этап 22.c).
 *
 * <p>MEMBER - только чтение, EDITOR - чтение + запись (PATCH metadata).
 * Удаление самой книги - только owner (createdBy), EDITOR этого не может.
 */
public final class BookMemberRole {

    public static final String MEMBER = "MEMBER";
    public static final String EDITOR = "EDITOR";

    private BookMemberRole() {
    }

    public static boolean isValid(String value) {
        return MEMBER.equals(value) || EDITOR.equals(value);
    }
}
