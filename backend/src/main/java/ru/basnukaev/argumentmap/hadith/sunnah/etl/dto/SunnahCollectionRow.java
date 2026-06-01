package ru.basnukaev.argumentmap.hadith.sunnah.etl.dto;

/**
 * Staging-строка сборника sunnah.com ({@code sn_staging_collection}).
 * Phase 5 ETL шаг 2 (ADR-051). Зеркалит логическую модель sunnah.com
 * (spec §5): {@code name} — идентификатор сборника ("bukhari"/"muslim"),
 * языковые поля денормализованы в ar/en.
 *
 * @param name PK, идентификатор сборника sunnah.com (slug)
 * @param hasBooks делится ли сборник на книги (nullable)
 * @param hasChapters делится ли на главы (nullable)
 * @param totalHadith всего хадисов (nullable)
 * @param totalAvailableHadith доступно хадисов с текстом (nullable)
 * @param titleAr арабское название (nullable)
 * @param titleEn английское название (nullable)
 * @param shortIntroAr краткое введение ar (nullable)
 * @param shortIntroEn краткое введение en (nullable)
 * @param rawJson полный исходный payload (jsonb, forward-compat)
 */
public record SunnahCollectionRow(
        String name,
        Boolean hasBooks,
        Boolean hasChapters,
        Integer totalHadith,
        Integer totalAvailableHadith,
        String titleAr,
        String titleEn,
        String shortIntroAr,
        String shortIntroEn,
        String rawJson
) {
}
