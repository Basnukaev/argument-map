package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

/**
 * Превью сборника из источника sunnah (до импорта) — bulk-policy gate.
 * Phase 5 ETL шаг 2.d.
 */
public record SunnahCollectionPreview(
        String name,
        String titleEn,
        String titleAr,
        Integer totalHadith,
        Boolean hasBooks,
        Boolean hasChapters
) {
}
