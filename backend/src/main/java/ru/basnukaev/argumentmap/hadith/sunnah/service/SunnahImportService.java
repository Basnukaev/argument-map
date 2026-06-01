package ru.basnukaev.argumentmap.hadith.sunnah.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDataSource;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahBookDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahChapterDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahCollectionDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahHadithDao;

/**
 * Оркестратор импорта sunnah.com: источник → staging (sn_staging_*) → hd_*.
 * Phase 5 ETL шаг 2.d.
 *
 * <p><b>Bulk-policy gate:</b> импорт строго <b>по одному сборнику</b> за
 * вызов (как у shamela — не массовый прогон вслепую). Источник передаётся
 * параметром ({@link SunnahDataSource}) — dump-reader сейчас, API-client
 * позже, без изменения сервиса.
 *
 * <p><b>Без оборачивающей транзакции:</b> чтение из внешнего источника
 * (MySQL-дамп) — вне транзакции Postgres; staging-upsert'ы идемпотентны
 * (ON CONFLICT), а атомарность записи в hd_* обеспечивает
 * {@code @Transactional} внутри {@link SunnahToHadithMapper#mapCollection}.
 * Повторный прогон безопасен (re-runnable).
 */
@Service
public class SunnahImportService {

    private static final Logger log = LoggerFactory.getLogger(SunnahImportService.class);

    private final SunnahCollectionDao collectionDao;
    private final SunnahBookDao bookDao;
    private final SunnahChapterDao chapterDao;
    private final SunnahHadithDao hadithDao;
    private final SunnahToHadithMapper mapper;

    public SunnahImportService(SunnahCollectionDao collectionDao,
                               SunnahBookDao bookDao,
                               SunnahChapterDao chapterDao,
                               SunnahHadithDao hadithDao,
                               SunnahToHadithMapper mapper) {
        this.collectionDao = collectionDao;
        this.bookDao = bookDao;
        this.chapterDao = chapterDao;
        this.hadithDao = hadithDao;
        this.mapper = mapper;
    }

    /**
     * Импортирует один сборник целиком: читает из источника, наполняет
     * staging, затем переносит в hd_* маппером.
     *
     * @throws IllegalArgumentException если сборника нет в источнике
     */
    public SunnahMappingResult importCollection(SunnahDataSource source, String collectionName) {
        // чтение из внешнего источника — вне транзакции Postgres
        List<SunnahCollectionRow> collections = source.readCollections().stream()
                .filter(c -> collectionName.equals(c.name()))
                .toList();
        if (collections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Сборник не найден в источнике sunnah: " + collectionName);
        }
        collectionDao.upsertAll(collections);
        int books = bookDao.upsertAll(source.readBooks(collectionName));
        int chapters = chapterDao.upsertAll(source.readChapters(collectionName));
        int hadiths = hadithDao.upsertAll(source.readHadiths(collectionName));
        log.info("sunnah staging залит {}: books={} chapters={} hadiths={}",
                collectionName, books, chapters, hadiths);

        return mapper.mapCollection(collectionName);
    }
}
