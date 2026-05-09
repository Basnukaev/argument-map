package ru.basnukaev.argumentmap.library.shamela.service;

/**
 * Результат {@code ShamelaImportService.syncMaster()}: показывает
 * изменилась ли версия каталога shamela и сколько строк попало в
 * staging при upsert.
 *
 * <p>Если {@link #changed()} == false, поля {@code categoriesCount}/
 * {@code authorsCount}/{@code booksCount} равны 0 - архив не качался,
 * upsert не выполнялся.
 */
public record MasterSyncResult(
        boolean changed,
        int previousVersion,
        int currentVersion,
        int categoriesCount,
        int authorsCount,
        int booksCount
) {

    public static MasterSyncResult unchanged(int version) {
        return new MasterSyncResult(false, version, version, 0, 0, 0);
    }

    public static MasterSyncResult synced(int previousVersion,
                                          int currentVersion,
                                          int categoriesCount,
                                          int authorsCount,
                                          int booksCount) {
        return new MasterSyncResult(true, previousVersion, currentVersion,
                categoriesCount, authorsCount, booksCount);
    }
}
