package ru.basnukaev.argumentmap.library.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Текущее состояние OCR задачи для page (Этап 17.b, ADR-041).
 * Возвращается {@code POST /api/v1/library/pages/{id}/ocr} (триггер)
 * и {@code GET /api/v1/library/pages/{id}/ocr} (статус-polling).
 *
 * @param pageId UUID страницы
 * @param status одно из {@code PENDING}/{@code PROCESSING}/{@code DONE}/
 *               {@code FAILED} либо {@code null} (OCR ещё не запускался
 *               для этой страницы либо у страницы нет image)
 * @param startedAt timestamp начала текущего/последнего OCR run
 * @param completedAt timestamp завершения (success или failure).
 *                    {@code null} пока {@code status=PROCESSING}
 * @param hasImage true если у страницы есть image scan (pre-condition
 *                 для OCR). Если false - triggering OCR бессмыслен,
 *                 фронт может скрыть кнопку
 */
public record OcrJobResponse(
        UUID pageId,
        String status,
        Instant startedAt,
        Instant completedAt,
        boolean hasImage
) {
}
