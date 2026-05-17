package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда topic_members запись с указанным id не найдена либо
 * принадлежит другой теме (ADR-043).
 */
public class TopicMemberNotFoundException extends RuntimeException {

    private final UUID memberId;

    public TopicMemberNotFoundException(UUID memberId) {
        super("Член темы не найден: " + memberId);
        this.memberId = memberId;
    }

    public UUID getMemberId() {
        return memberId;
    }
}
