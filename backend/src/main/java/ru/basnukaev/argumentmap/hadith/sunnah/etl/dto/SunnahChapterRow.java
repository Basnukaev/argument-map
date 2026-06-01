package ru.basnukaev.argumentmap.hadith.sunnah.etl.dto;

/**
 * Staging-строка главы книги sunnah.com ({@code sn_staging_chapter}).
 * Phase 5 ETL шаг 2 (ADR-051).
 *
 * <p>{@code chapterId} — числовой идентификатор главы (chapterId в
 * spec.v1.yml), часть PK. {@code chapterNumber*} — отображаемый номер
 * главы (может быть нечисловым), денормализован по языкам.
 *
 * @param collectionName часть PK (→ sn_staging_collection.name)
 * @param bookNumber часть PK (→ sn_staging_book.book_number)
 * @param chapterId часть PK, числовой id главы
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
        int chapterId,
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
