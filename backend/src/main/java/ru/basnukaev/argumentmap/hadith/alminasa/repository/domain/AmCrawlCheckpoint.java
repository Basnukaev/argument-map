package ru.basnukaev.argumentmap.hadith.alminasa.repository.domain;

import java.time.OffsetDateTime;

/**
 * Чекпоинт краулинга одного ES-индекса alminasa ({@code am_crawl_checkpoint}).
 * Курсор search_after — СОСТАВНОЙ (живой урок Сессии 56: hadith_serial_id —
 * номер внутри сборника, НЕ глобальный): {@code lastSortValue} — последний
 * {@code hadith_serial_id}, {@code lastSortId} — последний {@code hadith_id}
 * (уникальный tiebreaker). {@code fetchedCount} — абсолютное число
 * застейдженных хадисов на последней границе страницы.
 */
public record AmCrawlCheckpoint(
        String indexName,
        AmCrawlStatus status,
        Long lastSortValue,
        String lastSortId,
        long fetchedCount,
        Long totalHits,
        String error,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt
) {

    public enum AmCrawlStatus {
        IDLE, RUNNING, PAUSED, FAILED, COMPLETED
    }
}
