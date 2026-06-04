package ru.basnukaev.argumentmap.hadith.alminasa.web.dto;

import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCatalogService.CatalogEntry;

/**
 * Строка каталога сборника alminasa на admin-странице импорта (план 5).
 * {@code stagedCount} — застейджено в {@code am_staging_hadith};
 * {@code mappedCount} — смаплено в {@code hd_hadiths} (ТОЛЬКО alminasa-источник).
 *
 * @param bookId      id сборника в alminasa
 * @param slug        стабильный slug коллекции
 * @param nameAr      арабское название (staging book_name приоритетнее карты)
 * @param nameRu      русское название (из статической карты, nullable)
 * @param stagedCount число застейдженных доков
 * @param mappedCount число смапленных хадисов (alminasa-only)
 */
public record AlminasaCatalogEntryResponse(
        int bookId,
        String slug,
        String nameAr,
        String nameRu,
        long stagedCount,
        long mappedCount) {

    public static AlminasaCatalogEntryResponse from(CatalogEntry entry) {
        return new AlminasaCatalogEntryResponse(
                entry.bookId(), entry.slug(), entry.nameAr(), entry.nameRu(),
                entry.stagedCount(), entry.mappedCount());
    }
}
