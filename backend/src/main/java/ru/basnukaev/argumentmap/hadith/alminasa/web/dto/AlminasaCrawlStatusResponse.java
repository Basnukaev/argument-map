package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import java.time.OffsetDateTime;

import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;

/**
 * Статус краулинга alminasa: чекпоинт + счётчики staging-таблиц.
 * {@code status=IDLE} с нулями — краулинг ещё не запускался. Курсор —
 * составной (lastSortValue=serial внутри сборника, lastSortId=hadith_id).
 */
public record AlminasaCrawlStatusResponse(
        String status,
        Long lastSortValue,
        String lastSortId,
        long fetchedCount,
        Long totalHits,
        String error,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt,
        long stagedHadiths,
        long stagedNarrators,
        long stagedExplanations,
        long stagedRulings
) {

    public static AlminasaCrawlStatusResponse of(AmCrawlCheckpoint checkpoint,
                                                 long stagedHadiths,
                                                 long stagedNarrators,
                                                 long stagedExplanations,
                                                 long stagedRulings) {
        if (checkpoint == null) {
            return new AlminasaCrawlStatusResponse("IDLE", null, null, 0, null, null, null, null,
                    stagedHadiths, stagedNarrators, stagedExplanations, stagedRulings);
        }
        return new AlminasaCrawlStatusResponse(
                checkpoint.status().name(),
                checkpoint.lastSortValue(),
                checkpoint.lastSortId(),
                checkpoint.fetchedCount(),
                checkpoint.totalHits(),
                checkpoint.error(),
                checkpoint.startedAt(),
                checkpoint.updatedAt(),
                stagedHadiths, stagedNarrators, stagedExplanations, stagedRulings);
    }
}
