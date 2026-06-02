package ru.basnukaev.argumentmap.qa.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Голос пользователя за вопрос Q&amp;A (community-сигнал популярности за
 * вопрос&amp;ответ). 1 user - 1 vote на 1 вопрос. weight ∈ {-1, +1}:
 * -1 = downvote, +1 = upvote. Нейтральная позиция 0 не сохраняется -
 * вместо неё row удаляется.
 *
 * <p>Зеркалит {@link ru.basnukaev.argumentmap.domain.TopicVote} но на уровне
 * вопросов. Questions это open discussion (без visibility model) - голосовать
 * может любой authenticated user.
 */
public record QuestionVote(
        UUID id,
        UUID questionId,
        UUID userId,
        int weight,
        Instant votedAt
) {
}
