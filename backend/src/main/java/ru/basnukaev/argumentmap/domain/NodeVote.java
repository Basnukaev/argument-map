package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Голос пользователя за вес аргумента (узла). 1 user - 1 vote на 1 node.
 * weight ∈ {-1, +1}: -1 = "слабый/не согласен", +1 = "сильный/поддерживаю".
 * Нейтральная позиция 0 не сохраняется - вместо неё row удаляется.
 */
public record NodeVote(
        UUID id,
        UUID nodeId,
        UUID userId,
        int weight,
        Instant votedAt
) {
}
