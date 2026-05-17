package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда user не может редактировать/удалять вопрос - не автор
 * (asked_by) и не ADMIN (ADR-043 Amendment, Этап 22.c Q&amp;A guards).
 *
 * <p>Вопросы видны всем authenticated (open discussion), но
 * mutating-операции защищаются автором или ADMIN'ом.
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-question-write}.
 */
public class QuestionWriteAccessDeniedException extends RuntimeException {

    private final UUID questionId;
    private final UUID userId;

    public QuestionWriteAccessDeniedException(UUID questionId, UUID userId) {
        super("Пользователь " + userId + " не имеет прав на изменение вопроса " + questionId);
        this.questionId = questionId;
        this.userId = userId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public UUID getUserId() {
        return userId;
    }
}
