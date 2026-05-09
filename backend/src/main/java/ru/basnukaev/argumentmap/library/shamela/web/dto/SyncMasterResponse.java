package ru.basnukaev.argumentmap.library.shamela.web.dto;

/**
 * Ответ {@code POST /api/v1/admin/shamela/sync-master}.
 *
 * <p>{@code changed=false} - master-version в shamela совпала с
 * текущей в БД, ничего не качалось. counts равны 0.
 */
public record SyncMasterResponse(
        boolean changed,
        int previousVersion,
        int currentVersion,
        int categoriesCount,
        int authorsCount,
        int booksCount
) {
}
