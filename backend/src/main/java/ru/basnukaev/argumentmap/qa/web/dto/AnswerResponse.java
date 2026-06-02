package ru.basnukaev.argumentmap.qa.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * REST DTO ответа на вопрос (Этап 19.c, ADR-034).
 *
 * @param accepted  derived поле - {@code true} если этот ответ принят
 *                  (id совпадает с {@code questions.accepted_answer_id}
 *                  родителя). Заполняется в маппере controller'а
 * @param voteScore community-сигнал качества ответа = upvotes - downvotes
 *                  (нетто, может быть отрицательным). На list path заполнен
 *                  bulk-load из {@code answer_votes}; на mutating endpoint'ах
 *                  (create/update) default {@code 0}
 * @param userVote  голос вызывающего user'а ∈ {-1, +1, null} (null = не
 *                  голосовал либо anonymous). На list заполнен bulk-load;
 *                  на mutating - {@code null}
 */
public record AnswerResponse(
        UUID id,
        UUID questionId,
        String body,
        UUID authorId,
        Instant createdAt,
        Instant updatedAt,
        boolean accepted,
        int voteScore,
        Integer userVote
) {
}
