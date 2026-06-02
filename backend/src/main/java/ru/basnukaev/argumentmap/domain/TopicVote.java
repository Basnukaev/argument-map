package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Голос пользователя за тему (community-сигнал популярности). 1 user - 1 vote
 * на 1 тему. weight ∈ {-1, +1}: -1 = downvote, +1 = upvote. Нейтральная
 * позиция 0 не сохраняется - вместо неё row удаляется.
 *
 * <p>Зеркалит удалённый node_votes но на уровне тем. Узлы это curated expert
 * data - голосование за них семантически неверно; голоса переехали на темы
 * как сигнал популярности (ADR-053).
 */
public record TopicVote(
        UUID id,
        UUID topicId,
        UUID userId,
        int weight,
        Instant votedAt
) {
}
