package ru.basnukaev.argumentmap.hadith.alminasa.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.domain.AmCrawlCheckpoint.AmCrawlStatus;

/** DAO {@code am_crawl_checkpoint} (миграции 72-73). План 2 alminasa. */
@Repository
public class AmCrawlCheckpointDao {

    private static final String COLUMNS =
            "index_name, status, last_sort_value, last_sort_id, fetched_count, total_hits, error, started_at, updated_at";

    private static final RowMapper<AmCrawlCheckpoint> ROW_MAPPER = (rs, rn) -> new AmCrawlCheckpoint(
            rs.getString("index_name"),
            AmCrawlStatus.valueOf(rs.getString("status")),
            rs.getObject("last_sort_value", Long.class),
            rs.getString("last_sort_id"),
            rs.getLong("fetched_count"),
            rs.getObject("total_hits", Long.class),
            rs.getString("error"),
            rs.getObject("started_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public AmCrawlCheckpointDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AmCrawlCheckpoint> find(String indexName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM am_crawl_checkpoint WHERE index_name = ?",
                ROW_MAPPER, indexName
        ).stream().findFirst();
    }

    /**
     * Перевод в RUNNING (создаёт строку при отсутствии). {@code resetProgress}
     * — обнулить прогресс (свежий краулинг с нуля) или сохранить
     * курсор/fetched_count (resume после PAUSED/FAILED/stale).
     */
    public AmCrawlCheckpoint upsertRunning(String indexName, boolean resetProgress) {
        jdbcTemplate.update("""
                INSERT INTO am_crawl_checkpoint (index_name, status, started_at, updated_at)
                VALUES (?, 'RUNNING', now(), now())
                ON CONFLICT (index_name) DO UPDATE SET
                    status = 'RUNNING',
                    last_sort_value = CASE WHEN ? THEN NULL ELSE am_crawl_checkpoint.last_sort_value END,
                    last_sort_id    = CASE WHEN ? THEN NULL ELSE am_crawl_checkpoint.last_sort_id    END,
                    fetched_count   = CASE WHEN ? THEN 0    ELSE am_crawl_checkpoint.fetched_count   END,
                    error = NULL,
                    started_at = now(),
                    updated_at = now()
                """, indexName, resetProgress, resetProgress, resetProgress);
        return find(indexName).orElseThrow();
    }

    public void setTotalHits(String indexName, long totalHits) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET total_hits = ?, updated_at = now() WHERE index_name = ?",
                totalHits, indexName);
    }

    /**
     * Граница страницы: новый СОСТАВНОЙ search_after-курсор (serial +
     * hadith_id-tiebreaker) + АБСОЛЮТНЫЙ счётчик застейдженных (= count(*)
     * staging на момент границы; replay страницы не раздувает прогресс).
     */
    public void advance(String indexName, long lastSortValue, String lastSortId, long fetchedCount) {
        jdbcTemplate.update("""
                UPDATE am_crawl_checkpoint
                SET last_sort_value = ?, last_sort_id = ?, fetched_count = ?, updated_at = now()
                WHERE index_name = ?
                """, lastSortValue, lastSortId, fetchedCount, indexName);
    }

    public void markCompleted(String indexName) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET status = 'COMPLETED', error = NULL, updated_at = now() "
                        + "WHERE index_name = ?", indexName);
    }

    public void markPaused(String indexName) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET status = 'PAUSED', updated_at = now() "
                        + "WHERE index_name = ?", indexName);
    }

    public void markFailed(String indexName, String error) {
        jdbcTemplate.update(
                "UPDATE am_crawl_checkpoint SET status = 'FAILED', error = ?, updated_at = now() "
                        + "WHERE index_name = ?", error, indexName);
    }
}
