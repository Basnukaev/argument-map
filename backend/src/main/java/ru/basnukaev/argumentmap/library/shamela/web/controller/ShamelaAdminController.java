package ru.basnukaev.argumentmap.library.shamela.web.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaCategoryDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaSyncStateDao;
import ru.basnukaev.argumentmap.library.shamela.service.BookImportResult;
import ru.basnukaev.argumentmap.library.shamela.service.MappedBookResult;
import ru.basnukaev.argumentmap.library.shamela.service.MasterSyncResult;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaBookImportService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaMasterSyncService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaToLibraryMapper;
import ru.basnukaev.argumentmap.library.shamela.web.dto.ImportBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.MapBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.StagingBookSearchResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.SyncMasterResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.SyncStatusResponse;
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

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;

    private final ShamelaMasterSyncService masterSyncService;
    private final ShamelaBookImportService bookImportService;
    private final ShamelaToLibraryMapper mapper;
    private final ShamelaBookDao shamelaBookDao;
    private final ShamelaAuthorDao shamelaAuthorDao;
    private final ShamelaCategoryDao shamelaCategoryDao;
    private final ShamelaSyncStateDao syncStateDao;
    private final BookRepository bookRepository;

    public ShamelaAdminController(ShamelaMasterSyncService masterSyncService,
                                  ShamelaBookImportService bookImportService,
                                  ShamelaToLibraryMapper mapper,
                                  ShamelaBookDao shamelaBookDao,
                                  ShamelaAuthorDao shamelaAuthorDao,
                                  ShamelaCategoryDao shamelaCategoryDao,
                                  ShamelaSyncStateDao syncStateDao,
                                  BookRepository bookRepository) {
        this.masterSyncService = masterSyncService;
        this.bookImportService = bookImportService;
        this.mapper = mapper;
        this.shamelaBookDao = shamelaBookDao;
        this.shamelaAuthorDao = shamelaAuthorDao;
        this.shamelaCategoryDao = shamelaCategoryDao;
        this.syncStateDao = syncStateDao;
        this.bookRepository = bookRepository;
    }

    /**
     * Синхронизация каталога shamela: метаданные категорий/авторов/книг
     * в staging-таблицах {@code lib_shamela_*}. Если master-version
     * shamela совпала с текущей в БД - download не выполняется,
     * возвращается {@code changed=false}.
     */
    @PostMapping("/sync-master")
    public SyncMasterResponse syncMaster() {
        MasterSyncResult result = masterSyncService.syncMaster();
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
        BookImportResult result = bookImportService.importBook(bookId);
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

    /**
     * Поиск книг в staging-каталоге shamela. Используется admin-страницей
     * фронта для удобного выбора книги для импорта вместо ручного
     * SQL-копания. Возвращает не более {@code limit} записей с обогащением:
     * имя автора через JOIN + флаг {@code isMapped} (уже ли книга
     * замаплена в {@code lib_books}). По релевантности: точные совпадения
     * сначала, потом substring.
     *
     * <p>Tombstoned записи (deleted_at IS NOT NULL) исключаются.
     *
     * @param query  - подстрока для поиска по name (обязательный, NotBlank)
     * @param limit  - макс. количество результатов (default 20, max 100)
     */
    @GetMapping("/search")
    public List<StagingBookSearchResponse> searchBooks(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "параметр q обязателен и не должен быть пустым");
        }
        int effective = limit == null ? DEFAULT_SEARCH_LIMIT
                : Math.min(MAX_SEARCH_LIMIT, Math.max(1, limit));
        return shamelaBookDao.searchByName(query, effective).stream()
                .map(view -> new StagingBookSearchResponse(
                        view.id(),
                        view.name(),
                        view.authorName(),
                        view.majorRelease(),
                        view.isMapped()
                ))
                .toList();
    }

    /**
     * Состояние shamela ETL: версия master-каталога, время последнего
     * sync, размеры staging-таблиц + сколько книг уже замаплены в
     * lib_books. Для admin dashboard на фронте.
     */
    @GetMapping("/sync-status")
    public SyncStatusResponse syncStatus() {
        return new SyncStatusResponse(
                syncStateDao.getMasterVersion(),
                syncStateDao.getLastSyncedAt().orElse(null),
                shamelaCategoryDao.countAll(),
                shamelaAuthorDao.countAll(),
                shamelaBookDao.countAll(),
                bookRepository.countMappedFromShamela()
        );
    }

    private static void requirePositiveBookId(long bookId) {
        if (bookId < 1) {
            throw new IllegalArgumentException(
                    "bookId должен быть положительным, получено: " + bookId);
        }
    }
}
