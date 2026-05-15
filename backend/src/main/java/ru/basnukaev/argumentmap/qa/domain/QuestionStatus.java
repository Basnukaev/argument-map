package ru.basnukaev.argumentmap.qa.domain;

/**
 * Жизненный цикл вопроса в Q&amp;A приложении.
 *
 * <ul>
 *   <li>{@code OPEN} - только что задан, ждёт ответа</li>
 *   <li>{@code ANSWERED} - есть accepted answer (Этап 19.c, future)</li>
 *   <li>{@code CLOSED} - admin закрыл duplicate/spam/off-topic</li>
 * </ul>
 *
 * <p>На MVP (Этап 19.a) реально используются {@code OPEN} и {@code CLOSED}.
 * {@code ANSWERED} зарезервирован для будущей feature, чтобы избежать
 * миграции CHECK constraint позже.
 */
public enum QuestionStatus {
    OPEN,
    ANSWERED,
    CLOSED
}
