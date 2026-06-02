package ru.basnukaev.argumentmap.qa.web.dto;

import java.util.UUID;

/**
 * Ответ для GET /api/v1/questions/{id}/votes и POST /api/v1/questions/{id}/vote.
 * <ul>
 *   <li>questionId - идентификатор вопроса
 *   <li>upvotes/downvotes/score - агрегаты по {@link ru.basnukaev.argumentmap.domain.VoteStats}
 *   <li>userVote - текущий голос вызывающего user'а (-1, +1, либо null)
 * </ul>
 */
public record QuestionVoteStatsResponse(
        UUID questionId,
        int upvotes,
        int downvotes,
        int score,
        Integer userVote
) {
}
