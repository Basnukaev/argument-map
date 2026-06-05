package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/**
 * Строка {@code am_staging_commentary} (hadith-commentary-12, علل/иляль).
 * PK — commentary_id (природный int из {@code _source.commentary.id}).
 * {@code narrationsJson} — JSON-массив hadith_id-строк (ключ джойна на хадис),
 * хранится как jsonb (GIN-индекс под {@code @>}).
 */
public record AmCommentaryRow(
        int commentaryId,
        String bookName,
        String authorName,
        String narrationsJson,
        String rawJson
) {
}
