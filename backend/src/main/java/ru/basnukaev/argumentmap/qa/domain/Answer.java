package ru.basnukaev.argumentmap.qa.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ответ на question в Q&amp;A приложении (Этап 19.c, ADR-034).
 *
 * <p>На MVP - простая структура без voting, comments, nesting. Принятие
 * ответа моделируется как nullable FK в {@code questions.accepted_answer_id}
 * (migration 30), а не как boolean флаг на answer - это даёт встроенный
 * single-accepted invariant без CHECK constraint.
 *
 * @param questionId FK на questions(id), NOT NULL, ON DELETE CASCADE
 * @param authorId   FK на users(id), NOT NULL - заполняется из X-User-Id
 *                   через {@code CurrentUserArgumentResolver}
 */
public record Answer(
        UUID id,
        UUID questionId,
        String body,
        UUID authorId,
        Instant createdAt,
        Instant updatedAt
) {
}
