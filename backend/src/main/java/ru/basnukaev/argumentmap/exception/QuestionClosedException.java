package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Попытка операции жизненного цикла (принять ответ) над вопросом в
 * терминальном состоянии {@code CLOSED}. CLOSED ставит модератор/админ
 * (duplicate/spam/off-topic), и принятие ответа НЕ должно молча возвращать
 * вопрос в {@code ANSWERED} - это обошло бы модерацию. Маппится в 409
 * {@code question-closed} через GlobalExceptionHandler.
 */
public class QuestionClosedException extends RuntimeException {

    private final UUID questionId;

    public QuestionClosedException(UUID questionId) {
        super("Вопрос " + questionId + " закрыт - принятие ответа недоступно. "
                + "Сначала измените статус вопроса.");
        this.questionId = questionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }
}
