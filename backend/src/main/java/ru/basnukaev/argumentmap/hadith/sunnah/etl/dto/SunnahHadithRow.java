package ru.basnukaev.argumentmap.hadith.sunnah.etl.dto;

/**
 * Staging-строка хадиса sunnah.com ({@code sn_staging_hadith}).
 * Phase 5 ETL шаг 2 (ADR-051).
 *
 * <p>Ключевая особенность sunnah.com (spec §2.2): {@code bodyAr} —
 * это matn + isnad единым текстовым блоком. Структурированной цепочки
 * передатчиков НЕТ — она извлекается отдельной стадией IsnadExtraction
 * (шаг 3). На шаге 2 переносим только текст + grades + структуру.
 *
 * <p>{@code hadithNumber} — varchar: sunnah допускает суб-номера ("1a").
 * {@code urnAr}/{@code urnEn} — per-language URN (уникальный numeric id
 * хадиса на конкретном языке в sunnah.com).
 *
 * @param collectionName часть PK (→ sn_staging_collection.name)
 * @param hadithNumber часть PK, номер хадиса в сборнике (varchar)
 * @param bookNumber книга, к которой относится (nullable)
 * @param chapterId глава (nullable, hasChapters опционален)
 * @param urnAr URN арабской версии (nullable)
 * @param urnEn URN английской версии (nullable)
 * @param bodyAr арабский текст matn+isnad (nullable)
 * @param bodyEn английский перевод (nullable)
 * @param gradesJson оценки учёных как jsonb-массив [{graded_by, grade}] (nullable)
 * @param rawJson полный исходный payload (jsonb)
 */
public record SunnahHadithRow(
        String collectionName,
        String hadithNumber,
        String bookNumber,
        Integer chapterId,
        Long urnAr,
        Long urnEn,
        String bodyAr,
        String bodyEn,
        String gradesJson,
        String rawJson
) {
}
