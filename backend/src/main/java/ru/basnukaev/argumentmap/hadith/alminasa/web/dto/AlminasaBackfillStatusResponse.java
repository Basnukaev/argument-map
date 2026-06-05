package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import java.time.OffsetDateTime;

import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaDependentsBackfillService.State;

/**
 * Статус backfill-краула alminasa (علл/غريب, План 8): прогресс прогона +
 * счётчики staging-таблиц зависимых данных. {@code status=IDLE} с нулями —
 * backfill ещё не запускался (или завершён/упал — см. {@code error}).
 */
public record AlminasaBackfillStatusResponse(
        String status,
        OffsetDateTime startedAt,
        int processedPages,
        int processedHadiths,
        long stagedCommentaries,
        long stagedAmbiguous,
        String error
) {

    public static AlminasaBackfillStatusResponse of(State state,
                                                    long stagedCommentaries,
                                                    long stagedAmbiguous) {
        return new AlminasaBackfillStatusResponse(
                state.status().name(),
                state.startedAt(),
                state.processedPages(),
                state.processedHadiths(),
                stagedCommentaries,
                stagedAmbiguous,
                state.lastError());
    }
}
