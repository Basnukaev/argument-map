package ru.basnukaev.argumentmap.hadith.sunnah.service;

/**
 * Итог маппинга одного сборника sunnah.com → hd_* (Phase 5 ETL шаг 2.c).
 *
 * @param collectionName slug сборника
 * @param inserted сколько хадисов создано (hd_hadiths + primary hd_matns)
 * @param skippedExisting пропущено как уже импортированные (идемпотентность
 *        по (collection_id, primary_number))
 * @param skippedInvalid пропущено как непригодные (нечисловой номер или
 *        пустой арабский matn)
 */
public record SunnahMappingResult(
        String collectionName,
        int inserted,
        int skippedExisting,
        int skippedInvalid
) {
}
