package ru.basnukaev.argumentmap.hadith.sunnah.etl.dto;

/**
 * Staging-строка главы книги sunnah.com ({@code sn_staging_chapter}).
 * Phase 5 ETL шаг 2 (ADR-051).
 *
 * <p>{@code chapterId} — идентификатор главы (babID реального дампа),
 * часть PK. ДРОБНЫЙ (1.1, 22.10 — суб-главы), хранится как канонически
 * нормализованная строка (без хвостовых нулей). {@code chapterNumber*} —
 * отображаемый номер главы, денормализован по языкам.
 *
 * @param collectionName часть PK (→ sn_staging_collection.name)
 * @param bookNumber часть PK (→ sn_staging_book.book_number)
 * @param chapterId часть PK, id главы (канонический babID, напр. "1", "22.1")
 * @param chapterNumberAr отображаемый номер главы ar (nullable)
 * @param chapterNumberEn отображаемый номер главы en (nullable)
 * @param titleAr заголовок главы ar (nullable)
 * @param titleEn заголовок главы en (nullable)
 * @param introAr вступление главы ar (nullable)
 * @param introEn вступление главы en (nullable)
 * @param endingAr заключение главы ar (nullable)
 * @param endingEn заключение главы en (nullable)
 * @param rawJson полный исходный payload (jsonb)
 */
public record SunnahChapterRow(
        String collectionName,
        String bookNumber,
        String chapterId,
        String chapterNumberAr,
        String chapterNumberEn,
        String titleAr,
        String titleEn,
        String introAr,
        String introEn,
        String endingAr,
        String endingEn,
        String rawJson
) {
}
