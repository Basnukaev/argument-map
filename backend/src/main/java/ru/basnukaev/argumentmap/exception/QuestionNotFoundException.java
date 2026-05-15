package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Question с заданным id не существует. Этап 19.a.
 */
public class QuestionNotFoundException extends RuntimeException {

    private final UUID questionId;

    public QuestionNotFoundException(UUID questionId) {
        super("Вопрос не найден: " + questionId);
        this.questionId = questionId;
    }

    public UUID questionId() {
        return questionId;
    }
}
