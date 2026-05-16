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
 * @param askedBy          FK на users(id), NOT NULL - заполняется из
 *                         X-User-Id через {@code CurrentUserArgumentResolver}.
 *                         Миграция 27 добавила FK constraint после code review
 *                         Сессии 35
 * @param acceptedAnswerId nullable FK на answers(id) - принятый ответ
 *                         (Этап 19.c, ADR-034). Migration 30 добавила колонку.
 *                         {@code null} = ответ не принят (status обычно OPEN
 *                         или CLOSED). Не {@code null} = status обычно
 *                         ANSWERED. ON DELETE SET NULL семантика
 */
public record Question(
        UUID id,
        String title,
        String body,
        QuestionStatus status,
        UUID askedBy,
        UUID acceptedAnswerId,
        Instant createdAt,
        Instant updatedAt
) {
}
