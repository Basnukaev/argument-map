package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

/**
 * Превью сборника из источника sunnah (до импорта) — bulk-policy gate.
 * Phase 5 ETL шаг 2.d.
 *
 * @param totalHadith    каталожное количество хадисов ({@code Collections.totalhadith}
 *                       — полный корпус по версии sunnah.com; не отражает объём
 *                       загруженного дампа)
 * @param availableHadith фактическое количество строк {@code HadithTable} для этого
 *                       сборника в загруженном дампе (0 если коллекция есть в каталоге,
 *                       но её хадисов в дампе нет)
 */
public record SunnahCollectionPreview(
        String name,
        String titleEn,
        String titleAr,
        Integer totalHadith,
        Integer availableHadith,
        Boolean hasBooks,
        Boolean hasChapters
) {
}
