package ru.basnukaev.argumentmap.qa.web.dto;

import java.util.UUID;

/**
 * Ответ для GET /api/v1/answers/{id}/vote и POST /api/v1/answers/{id}/vote.
 * <ul>
 *   <li>answerId - идентификатор ответа
 *   <li>upvotes/downvotes/score - агрегаты по {@link ru.basnukaev.argumentmap.domain.VoteStats}
 *   <li>userVote - текущий голос вызывающего user'а (-1, +1, либо null)
 * </ul>
 */
public record AnswerVoteStatsResponse(
        UUID answerId,
        int upvotes,
        int downvotes,
        int score,
        Integer userVote
) {
}
