package ru.basnukaev.argumentmap.qa.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Вопрос в Q&amp;A приложении (Этап 19.a, ADR-032).
 *
 * <p>На MVP это standalone сущность - source attachments (через будущую
 * {@code question_sources} таблицу) откладываются на Этап 19.b. Связь
 * с Source/Book stack из ADR-018 будет через {@code question_sources}
 * паттерн аналогичный {@code node_sources}, доказывая что platform
 * archmodel переиспользуется.
 *
 * @param askedBy nullable - на MVP нет auth, заполняется из X-User-Id
 */
public record Question(
        UUID id,
        String title,
        String body,
        QuestionStatus status,
        UUID askedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
