package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

import java.util.List;

/**
 * DRY-RUN превью маппинга одного хадиса sunnah.com в наш формат {@code hd_*}
 * — БЕЗ записи в БД (ADR-052, фазовый импорт). Ключевая фича: admin видит
 * во что превратится хадис ПРЕЖДЕ чем коммитить.
 *
 * <p>Гарантия точности: превью строится тем же кодом импорта (тот же mapper,
 * та же чистка/нормализация/разбор grades), прогнанным в транзакции с
 * принудительным rollback (см. {@code SunnahImportService.previewSingle}) —
 * поэтому превью В ТОЧНОСТИ равно тому, что создал бы реальный импорт.
 *
 * @param collection slug сборника (bukhari/muslim…)
 * @param primaryNumber распарсенный числовой номер (null если нечисловой → не
 *        был бы импортирован)
 * @param status статус будущего хадиса (VARIANT для импорта sunnah)
 * @param matnAr очищенный арабский matn (как лёг бы в hd_matns.text_ar)
 * @param matnEn очищенный английский текст (hd_matns.text_en, nullable)
 * @param normalizedMatn нормализованный арабский (search-форма, hd_*.normalized)
 * @param grades разобранные оценки [{scholar, grade}] (пустой если нет)
 * @param structure книга/глава из метаданных источника (nullable поля внутри)
 * @param isnad структурный иснад если пайплайн его выводит (пока null — sunnah
 *        даёт matn+isnad блобом, отдельная стадия IsnadExtraction)
 * @param importable можно ли импортировать (false → нечисловой номер или
 *        пустой арабский matn, хадис был бы пропущен в skippedInvalid)
 * @param alreadyImported уже есть hd_hadiths для (collection, primaryNumber)
 */
public record SunnahHadithPreview(
        String collection,
        Integer primaryNumber,
        String status,
        String matnAr,
        String matnEn,
        String normalizedMatn,
        List<GradeView> grades,
        Structure structure,
        Object isnad,
        boolean importable,
        boolean alreadyImported
) {

    /** Оценка учёного в формате нашего hd_hadiths.metadata.grades. */
    public record GradeView(String scholar, String grade) {
    }

    /** Структура книга/глава из метаданных источника (как лёг бы в hd_matns.metadata). */
    public record Structure(
            String bookNumber,
            String bookNameAr,
            String bookNameEn,
            String chapterId,
            String chapterTitleAr,
            String chapterTitleEn
    ) {
    }
}
