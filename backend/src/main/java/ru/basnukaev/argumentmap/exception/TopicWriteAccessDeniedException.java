package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда user может читать тему, но не имеет права писать
 * (ADR-043). Типично - SHARED тема с ролью MEMBER, либо PUBLIC тема
 * для non-owner.
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-topic-write}.
 */
public class TopicWriteAccessDeniedException extends RuntimeException {

    private final UUID topicId;
    private final UUID userId;

    public TopicWriteAccessDeniedException(UUID topicId, UUID userId) {
        super("Пользователь " + userId + " не имеет прав на запись в тему " + topicId);
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
