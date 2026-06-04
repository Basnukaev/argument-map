package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao.StagedBook;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCollections.CollectionInfo;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;

/**
 * Каталог 12 сборников alminasa для admin-страницы импорта (план 5, решение 1).
 *
 * <p>Всегда возвращает все 12 строк {@link AlminasaCollections#all()} даже при
 * пустом staging — карта статична. Накладывает staged-прогресс
 * ({@link AmHadithStagingDao#catalogByBook()}) и mapped-прогресс
 * ({@link HadithRepository#countByCollectionGroupedForSource}) по slug → collection.
 *
 * <p>mappedCount — ТОЛЬКО alminasa-хадисы (фикс C1): legacy sunnah-строка того
 * же сборника в dev-БД не должна искажать прогресс. {@code findBySlug} пуст
 * (сборник ещё не создавался маппером) → mappedCount=0 (не ошибка).
 * {@code nameAr}: staging {@code book_name} приоритетнее карты (док авторитетнее).
 */
@Service
public class AlminasaCatalogService {

    /** Источник alminasa-хадисов в {@code hd_hadiths.external_source}. */
    private static final String SOURCE = "alminasa";

    private final AmHadithStagingDao hadithStagingDao;
    private final CollectionRepository collectionRepository;
    private final HadithRepository hadithRepository;

    public AlminasaCatalogService(AmHadithStagingDao hadithStagingDao,
                                  CollectionRepository collectionRepository,
                                  HadithRepository hadithRepository) {
        this.hadithStagingDao = hadithStagingDao;
        this.collectionRepository = collectionRepository;
        this.hadithRepository = hadithRepository;
    }

    /** Каталог всех 12 сборников со staged/mapped прогрессом. */
    public List<CatalogEntry> catalog() {
        Map<Integer, StagedBook> stagedByBookId = new java.util.HashMap<>();
        for (StagedBook staged : hadithStagingDao.catalogByBook()) {
            stagedByBookId.put(staged.bookId(), staged);
        }
        Map<UUID, Long> mappedByCollection = hadithRepository.countByCollectionGroupedForSource(SOURCE);

        List<CatalogEntry> result = new ArrayList<>();
        for (Map.Entry<Integer, CollectionInfo> e : AlminasaCollections.all().entrySet()) {
            int bookId = e.getKey();
            CollectionInfo info = e.getValue();
            StagedBook staged = stagedByBookId.get(bookId);

            long stagedCount = staged == null ? 0L : staged.stagedCount();
            String nameAr = staged != null && staged.bookName() != null
                    ? staged.bookName() : info.nameAr();

            Optional<Collection> collection = collectionRepository.findBySlug(info.slug());
            long mappedCount = collection
                    .map(c -> mappedByCollection.getOrDefault(c.id(), 0L))
                    .orElse(0L);

            result.add(new CatalogEntry(
                    bookId, info.slug(), nameAr, info.nameRu(), stagedCount, mappedCount));
        }
        return result;
    }

    /** Строка каталога сборника: прогресс staged → mapped (alminasa-only). */
    public record CatalogEntry(
            int bookId,
            String slug,
            String nameAr,
            String nameRu,
            long stagedCount,
            long mappedCount) {
    }
}
