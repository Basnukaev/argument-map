package ru.basnukaev.argumentmap.domain;

/**
 * Константы entity_type для audit_log (Этап 22.d, ADR-043 Amendment 3).
 * Хранится как строка в БД - не enum, чтобы добавление новых типов
 * не требовало миграцию. Валидация через {@link #isValid(String)}.
 */
public final class AuditEntityType {

    public static final String TOPIC = "TOPIC";
    public static final String NODE = "NODE";
    public static final String EDGE = "EDGE";
    public static final String BOOK = "BOOK";
    public static final String QUESTION = "QUESTION";
    public static final String ANSWER = "ANSWER";
    public static final String TOPIC_MEMBER = "TOPIC_MEMBER";
    public static final String BOOK_MEMBER = "BOOK_MEMBER";
    public static final String NODE_SOURCE = "NODE_SOURCE";
    public static final String QUESTION_SOURCE = "QUESTION_SOURCE";
    public static final String ANSWER_SOURCE = "ANSWER_SOURCE";

    private AuditEntityType() {
    }

    public static boolean isValid(String type) {
        return TOPIC.equals(type) || NODE.equals(type) || EDGE.equals(type)
                || BOOK.equals(type) || QUESTION.equals(type) || ANSWER.equals(type)
                || TOPIC_MEMBER.equals(type) || BOOK_MEMBER.equals(type)
                || NODE_SOURCE.equals(type) || QUESTION_SOURCE.equals(type)
                || ANSWER_SOURCE.equals(type);
    }
}
