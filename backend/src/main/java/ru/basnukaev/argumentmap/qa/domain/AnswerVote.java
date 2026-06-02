package ru.basnukaev.argumentmap.qa.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Голос пользователя за отдельный ответ Q&amp;A (community-сигнал качества
 * конкретного ответа). 1 user - 1 vote на 1 ответ. weight ∈ {-1, +1}:
 * -1 = downvote, +1 = upvote. Нейтральная позиция 0 не сохраняется -
 * вместо неё row удаляется.
 *
 * <p>Зеркалит {@link QuestionVote} но на уровне ответов. Answers это open
 * discussion (без visibility model) - голосовать может любой authenticated
 * user.
 */
public record AnswerVote(
        UUID id,
        UUID answerId,
        UUID userId,
        int weight,
        Instant votedAt
) {
}
