package ru.basnukaev.argumentmap.library.shamela.web.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.library.shamela.service.BookImportResult;
import ru.basnukaev.argumentmap.library.shamela.service.MappedBookResult;
import ru.basnukaev.argumentmap.library.shamela.service.MasterSyncResult;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaImportService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaToLibraryMapper;
import ru.basnukaev.argumentmap.library.shamela.web.dto.ImportBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.MapBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.SyncMasterResponse;
import ru.basnukaev.argumentmap.library.shamela.web.mapper.ShamelaAdminMappers;
import ru.basnukaev.argumentmap.web.CurrentUser;

/**
 * Admin REST endpoints для shamela ETL pipeline (Этап 15.6).
 *
 * <p>На MVP - без role-check авторизации. {@link CurrentUser} только
 * для получения user-id (передаётся в {@code mapBook} как
 * {@code created_by} в {@code lib_books}). Spring Security + admin
 * role появятся в Этапе 20.
 *
 * <p>{@code POST} операции - синхронные. {@code sync-master} может
 * занять до минуты при первом полном sync, {@code import-book} ~5с
 * на одну книгу, {@code map-book} ~1с. Async через @Async или
 * message queue - future task.
 *
 * <p>PDF download endpoint отложен в follow-up - требует streaming
 * response через {@code StreamingResponseBody} и cleanup tempfile
 * после ответа. На MVP не критичен (lazy по природе, см. ADR-020).
 */
@RestController
@RequestMapping("/api/v1/admin/shamela")
public class ShamelaAdminController {

    private final ShamelaImportService importService;
    private final ShamelaToLibraryMapper mapper;

    public ShamelaAdminController(ShamelaImportService importService,
                                  ShamelaToLibraryMapper mapper) {
        this.importService = importService;
        this.mapper = mapper;
    }

    /**
     * Синхронизация каталога shamela: метаданные категорий/авторов/книг
     * в staging-таблицах {@code lib_shamela_*}. Если master-version
     * shamela совпала с текущей в БД - download не выполняется,
     * возвращается {@code changed=false}.
     */
    @PostMapping("/sync-master")
    public SyncMasterResponse syncMaster() {
        MasterSyncResult result = importService.syncMaster();
        return ShamelaAdminMappers.toResponse(result);
    }

    /**
     * Загрузка контента конкретной книги (page+title) в staging.
     * Книга уже должна быть в {@code lib_shamela_book} (из предыдущего
     * sync-master), иначе 404.
     */
    @PostMapping("/import-book/{bookId}")
    public ImportBookResponse importBook(@PathVariable long bookId) {
        requirePositiveBookId(bookId);
        BookImportResult result = importService.importBook(bookId);
        return ShamelaAdminMappers.toResponse(result);
    }

    /**
     * Маппинг книги из staging в доменную модель {@code lib_books}/
     * {@code lib_chapters}/{@code lib_pages}/{@code authorities}.
     * После этого книга появляется в обычном {@code GET /api/v1/library/books/{id}}.
     *
     * <p>Re-import idempotent: если книга уже была замаплена,
     * возвращается существующая ({@code created=false}).
     */
    @PostMapping("/map-book/{bookId}")
    public MapBookResponse mapBook(@PathVariable long bookId,
                                   @CurrentUser UUID currentUserId) {
        requirePositiveBookId(bookId);
        MappedBookResult result = mapper.mapBook(bookId, currentUserId);
        return ShamelaAdminMappers.toResponse(result);
    }

    private static void requirePositiveBookId(long bookId) {
        if (bookId < 1) {
            throw new IllegalArgumentException(
                    "bookId должен быть положительным, получено: " + bookId);
        }
    }
}
