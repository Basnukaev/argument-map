package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда user не имеет права читать тему (ADR-043).
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-topic-access}. topicId и userId включаются
 * в properties для debugging.
 */
public class TopicAccessDeniedException extends RuntimeException {

    private final UUID topicId;
    private final UUID userId;

    public TopicAccessDeniedException(UUID topicId, UUID userId) {
        super("Пользователь " + userId + " не имеет доступа на чтение к теме " + topicId);
        this.topicId = topicId;
        this.userId = userId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public UUID getUserId() {
        return userId;
    }
}
