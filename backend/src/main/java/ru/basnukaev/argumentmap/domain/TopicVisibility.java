package ru.basnukaev.argumentmap.domain;

/**
 * Уровни visibility темы (ADR-043). String-литералы соответствуют
 * CHECK constraint в БД, поэтому не enum (тот же подход что
 * {@link ru.basnukaev.argumentmap.auth.domain.UserRole}).
 */
public final class TopicVisibility {

    public static final String PRIVATE = "PRIVATE";
    public static final String SHARED = "SHARED";
    public static final String PUBLIC = "PUBLIC";

    private TopicVisibility() {
    }

    public static boolean isValid(String value) {
        return PRIVATE.equals(value) || SHARED.equals(value) || PUBLIC.equals(value);
    }
}
