package ru.basnukaev.argumentmap.qa.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * REST DTO ответа на вопрос (Этап 19.c, ADR-034).
 *
 * @param accepted derived поле - {@code true} если этот ответ принят
 *                 (id совпадает с {@code questions.accepted_answer_id}
 *                 родителя). Заполняется в маппере controller'а
 */
public record AnswerResponse(
        UUID id,
        UUID questionId,
        String body,
        UUID authorId,
        Instant createdAt,
        Instant updatedAt,
        boolean accepted
) {
}
