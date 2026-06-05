package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/**
 * Строка {@code am_staging_ambiguous} (ambiguous-12, غريب/гариб).
 * PK — ambiguous_id (природный int из {@code _source.id}). Полный _source
 * (включая длинный {@code explanation}) едет в raw jsonb.
 */
public record AmAmbiguousRow(
        int ambiguousId,
        String bookName,
        String author,
        String rawJson
) {
}
