package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import java.util.List;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;

/**
 * Абстракция источника данных sunnah.com (Phase 5 ETL шаг 2, спека §11).
 *
 * <p>Решение Абдулы — ДВА источника: open dump ({@code SunnahDumpReader},
 * MySQL-дамп {@code github.com/sunnah-com/api}) и REST API
 * ({@code SunnahApiClient}, шаг 4, proxy-aware). Обе реализации отдают
 * данные на staging-уровне (DTO-records), их пишут в одни {@code sn_staging_*},
 * затем единый {@code SunnahToHadithMapper} наполняет {@code hd_*}.
 *
 * <p>Гранулярность — по сборнику: {@link #readCollections()} перечисляет
 * каталог доступных сборников, остальные методы тянут содержимое одного
 * сборника по его {@code name}. Это совпадает с пилотом (Бухари, затем
 * Муслим) и bulk-policy gate (импорт по одному сборнику, превью до commit).
 */
public interface SunnahDataSource {

    /** Каталог доступных сборников (bukhari, muslim, …). */
    List<SunnahCollectionRow> readCollections();

    /** Книги (разделы) одного сборника. */
    List<SunnahBookRow> readBooks(String collectionName);

    /** Главы одного сборника. */
    List<SunnahChapterRow> readChapters(String collectionName);

    /** Хадисы одного сборника (текст + grades, без структурного иснада). */
    List<SunnahHadithRow> readHadiths(String collectionName);
}
