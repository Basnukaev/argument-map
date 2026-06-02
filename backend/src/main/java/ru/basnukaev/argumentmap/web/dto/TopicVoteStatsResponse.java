package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

/**
 * Ответ для GET /api/v1/topics/{id}/votes и POST /api/v1/topics/{id}/vote.
 * <ul>
 *   <li>topicId - идентификатор темы
 *   <li>upvotes/downvotes/score - агрегаты по {@link ru.basnukaev.argumentmap.domain.VoteStats}
 *   <li>userVote - текущий голос вызывающего user'а (-1, +1, либо null)
 * </ul>
 */
public record TopicVoteStatsResponse(
        UUID topicId,
        int upvotes,
        int downvotes,
        int score,
        Integer userVote
) {
}
