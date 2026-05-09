package ru.basnukaev.argumentmap.library.shamela.web.dto;

import java.time.OffsetDateTime;

/**
 * Состояние shamela ETL для admin dashboard
 * ({@code GET /api/v1/admin/shamela/sync-status}).
 *
 * <p>{@code lastSyncedAt = null} означает что {@code syncMaster()} ещё
 * не выполнялся (свежая БД). После первого sync-master будет содержать
 * UTC-метку.
 */
public record SyncStatusResponse(
        int masterVersion,
        OffsetDateTime lastSyncedAt,
        int categoriesCount,
        int authorsCount,
        int booksCount,
        int mappedBooksCount
) {
}
