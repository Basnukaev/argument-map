package ru.basnukaev.argumentmap.library.shamela.etl.dto;

/**
 * Строка из {@code category.sqlite} master-архива shamela.
 * После null-safe парсинга через {@link ru.basnukaev.argumentmap.library.shamela.etl.SqliteValueParser}
 * - готова к bulk upsert в {@code lib_shamela_category} через
 * {@code ShamelaCategoryDao}.
 *
 * @param deleted флаг tombstone из shamela ({@code is_deleted='1'}).
 *                В целевой таблице транслируется в {@code deleted_at = now()}
 *                либо остаётся {@code NULL}.
 */
public record ShamelaCategoryRow(
        long id,
        String name,
        Integer displayOrder,
        boolean deleted
) {
}
