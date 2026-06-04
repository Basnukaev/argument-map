package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_narrator}. id — numeric id alminasa (из ES _id). */
public record AmNarratorRow(
        long narratorId,
        String fullName,
        String grade,
        String level,
        String rawJson
) {
}
