package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import java.time.OffsetDateTime;

import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaNarratorCommentaryBackfillService.State;

/**
 * Статус backfill-краула джарх/таʿдиль о рави (narrator-commentary, ADR-061):
 * прогресс прогона + счётчик staging-таблицы цитат. {@code status=IDLE} с
 * нулями — backfill ещё не запускался (или завершён/упал — см. {@code error}).
 */
public record AlminasaNarratorCommentaryBackfillStatusResponse(
        String status,
        OffsetDateTime startedAt,
        int processedPages,
        int processedNarrators,
        long stagedCommentaries,
        String error
) {

    public static AlminasaNarratorCommentaryBackfillStatusResponse of(State state,
                                                                      long stagedCommentaries) {
        return new AlminasaNarratorCommentaryBackfillStatusResponse(
                state.status().name(),
                state.startedAt(),
                state.processedPages(),
                state.processedNarrators(),
                stagedCommentaries,
                state.lastError());
    }
}
