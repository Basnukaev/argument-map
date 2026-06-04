package ru.basnukaev.argumentmap.hadith.alminasa.repository.domain;

import java.time.OffsetDateTime;

/**
 * Чекпоинт краулинга одного ES-индекса alminasa ({@code am_crawl_checkpoint}).
 * {@code lastSortValue} — последний {@code hadith_serial_id} (search_after).
 * {@code fetchedCount} — абсолютное число застейдженных хадисов на последней
 * границе страницы.
 */
public record AmCrawlCheckpoint(
        String indexName,
        AmCrawlStatus status,
        Long lastSortValue,
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
