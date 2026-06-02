package ru.basnukaev.argumentmap.library.archiveorg;

import java.util.UUID;

/**
 * Результат {@code POST /api/v1/admin/archive-org/import} (ADR-056).
 *
 * @param bookId             id созданной (или найденной при idempotency) книги
 * @param archiveOrgId       natural key
 * @param volumesRegistered  число томов записанных в pdf_links (без обложки)
 * @param coverSet           была ли установлена cover_url
 * @param pagesExtracted     сколько lib_pages создано (0 если extractText=false)
 * @param alreadyExisted     true если книга уже была импортирована (idempotent hit)
 */
public record ArchiveOrgImportResponse(
        UUID bookId,
        String archiveOrgId,
        int volumesRegistered,
        boolean coverSet,
        int pagesExtracted,
        boolean alreadyExisted
) {
}
