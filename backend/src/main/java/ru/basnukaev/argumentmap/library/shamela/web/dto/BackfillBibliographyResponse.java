package ru.basnukaev.argumentmap.library.shamela.web.dto;

/**
 * Результат bulk-backfill academic metadata.
 *
 * @param scanned всего книг shamela-sourced проверено
 * @param updated книги где parser нашёл хотя бы одно поле и сделал UPDATE
 * @param skipped книги где parser ничего не выловил (blank description
 *                / нет markers / выловленные значения совпали с уже
 *                существующими) или backfill упал на этой книге
 */
public record BackfillBibliographyResponse(
        int scanned,
        int updated,
        int skipped
) {
}
