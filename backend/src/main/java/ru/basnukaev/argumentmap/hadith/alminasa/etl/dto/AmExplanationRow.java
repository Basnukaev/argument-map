package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_explanation}. PK — ES _id (индекс-снапшот иммутабелен). */
public record AmExplanationRow(
        String esId,
        String hadithId,
        String bookName,
        String author,
        String rawJson
) {
}
