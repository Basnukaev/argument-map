package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Текущее состояние AI edit задачи для page (Этап 17.e, ADR-042).
 * Возвращается {@code POST /api/v1/library/pages/{id}/ai-edit} (триггер)
 * и {@code GET /api/v1/library/pages/{id}/ai-edit} (polling).
 *
 * @param pageId UUID страницы
 * @param status одно из {@code PENDING}/{@code PROCESSING}/{@code DONE}/
 *               {@code FAILED} либо {@code null} (AI edit ещё не
 *               запускался для этой страницы)
 * @param startedAt timestamp начала текущего/последнего AI edit run
 * @param completedAt timestamp завершения (success или failure).
 *                    {@code null} пока {@code status=PROCESSING}
 * @param hasTextContent true если у страницы есть text_content
 *                       (precondition для AI edit - нет текста, нечего
 *                       структурировать). Frontend может скрыть кнопку
 *                       при false
 */
public record AiEditJobResponse(
        UUID pageId,
        String status,
        Instant startedAt,
        Instant completedAt,
        boolean hasTextContent
) {
}
