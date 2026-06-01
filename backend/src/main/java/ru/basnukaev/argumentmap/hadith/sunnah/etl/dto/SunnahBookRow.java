package ru.basnukaev.argumentmap.hadith.sunnah.etl.dto;

/**
 * Staging-строка книги (раздела) сборника sunnah.com
 * ({@code sn_staging_book}). Phase 5 ETL шаг 2 (ADR-051).
 *
 * <p>{@code bookNumber} — varchar: sunnah.com допускает нечисловые
 * значения ("introduction") и потому хранится как текст.
 *
 * @param collectionName FK + часть PK (→ sn_staging_collection.name)
 * @param bookNumber часть PK, номер книги внутри сборника (varchar)
 * @param hadithStartNumber первый номер хадиса в книге (nullable)
 * @param hadithEndNumber последний номер хадиса (nullable)
 * @param numberOfHadith число хадисов в книге (nullable)
 * @param nameAr арабское название книги (nullable)
 * @param nameEn английское название книги (nullable)
 * @param rawJson полный исходный payload (jsonb)
 */
public record SunnahBookRow(
        String collectionName,
        String bookNumber,
        Integer hadithStartNumber,
        Integer hadithEndNumber,
        Integer numberOfHadith,
        String nameAr,
        String nameEn,
        String rawJson
) {
}
