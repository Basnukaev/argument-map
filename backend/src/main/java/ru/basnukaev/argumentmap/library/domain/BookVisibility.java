package ru.basnukaev.argumentmap.library.domain;

/**
 * Уровни visibility книги (ADR-043 Amendment, Этап 22.c). Семантика
 * та же что у {@link ru.basnukaev.argumentmap.domain.TopicVisibility}.
 * String-литералы соответствуют CHECK constraint lib_books_visibility_check.
 */
public final class BookVisibility {

    public static final String PRIVATE = "PRIVATE";
    public static final String SHARED = "SHARED";
    public static final String PUBLIC = "PUBLIC";

    private BookVisibility() {
    }

    public static boolean isValid(String value) {
        return PRIVATE.equals(value) || SHARED.equals(value) || PUBLIC.equals(value);
    }
}
