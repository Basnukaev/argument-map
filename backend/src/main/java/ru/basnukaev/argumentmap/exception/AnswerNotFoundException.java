package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Answer с заданным id не существует. Этап 19.c, ADR-034.
 */
public class AnswerNotFoundException extends RuntimeException {

    private final UUID answerId;

    public AnswerNotFoundException(UUID answerId) {
        super("Ответ не найден: " + answerId);
        this.answerId = answerId;
    }

    public UUID answerId() {
        return answerId;
    }
}
