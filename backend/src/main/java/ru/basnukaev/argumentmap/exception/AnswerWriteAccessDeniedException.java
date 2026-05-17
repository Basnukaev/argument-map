package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда user не может редактировать/удалять ответ - не автор
 * и не ADMIN (ADR-043 Amendment, Этап 22.c Q&amp;A guards).
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-answer-write}.
 */
public class AnswerWriteAccessDeniedException extends RuntimeException {

    private final UUID answerId;
    private final UUID userId;

    public AnswerWriteAccessDeniedException(UUID answerId, UUID userId) {
        super("Пользователь " + userId + " не имеет прав на изменение ответа " + answerId);
        this.answerId = answerId;
        this.userId = userId;
    }

    public UUID getAnswerId() {
        return answerId;
    }

    public UUID getUserId() {
        return userId;
    }
}
