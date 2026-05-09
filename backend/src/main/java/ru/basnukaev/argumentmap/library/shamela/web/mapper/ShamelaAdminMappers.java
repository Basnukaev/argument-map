package ru.basnukaev.argumentmap.library.shamela.web.mapper;

import ru.basnukaev.argumentmap.library.shamela.service.BookImportResult;
import ru.basnukaev.argumentmap.library.shamela.service.MappedBookResult;
import ru.basnukaev.argumentmap.library.shamela.service.MasterSyncResult;
import ru.basnukaev.argumentmap.library.shamela.web.dto.ImportBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.MapBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.SyncMasterResponse;

/**
 * Маппинг service-records → web-DTO для shamela admin endpoints.
 * Сейчас 1-to-1 ре-shape, отдельные DTO нужны для forward-compat
 * (например, добавление HATEOAS-ссылок или camelCase-rename без
 * тёгания service-слоя).
 */
public final class ShamelaAdminMappers {

    private ShamelaAdminMappers() {
    }

    public static SyncMasterResponse toResponse(MasterSyncResult result) {
        return new SyncMasterResponse(
                result.changed(),
                result.previousVersion(),
                result.currentVersion(),
                result.categoriesCount(),
                result.authorsCount(),
                result.booksCount()
        );
    }

    public static ImportBookResponse toResponse(BookImportResult result) {
        return new ImportBookResponse(
                result.bookId(),
                result.majorRelease(),
                result.pagesCount(),
                result.titlesCount()
        );
    }

    public static MapBookResponse toResponse(MappedBookResult result) {
        return new MapBookResponse(
                result.bookId(),
                result.shamelaBookId(),
                result.created(),
                result.authorityId(),
                result.chaptersCount(),
                result.pagesCount()
        );
    }
}
