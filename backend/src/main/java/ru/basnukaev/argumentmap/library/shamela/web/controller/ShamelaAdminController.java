package ru.basnukaev.argumentmap.library.shamela.web.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaCategoryDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaSyncStateDao;
import ru.basnukaev.argumentmap.library.shamela.service.BookImportResult;
import ru.basnukaev.argumentmap.library.shamela.service.MappedBookResult;
import ru.basnukaev.argumentmap.library.shamela.service.MasterSyncResult;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaBibliographyBackfillService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaBookImportService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaMasterSyncService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaToLibraryMapper;
import ru.basnukaev.argumentmap.library.shamela.web.dto.BackfillBibliographyResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.ImportBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.MapBookResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.StagingBookSearchResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.SyncMasterResponse;
import ru.basnukaev.argumentmap.library.shamela.web.dto.SyncStatusResponse;
import ru.basnukaev.argumentmap.library.shamela.web.mapper.ShamelaAdminMappers;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

/**
 * Admin REST endpoints для shamela ETL pipeline (Этап 15.6).
 *
 * <p><b>ADMIN-only</b> (консистентно с {@code SunnahAdminController} и
 * audit admin endpoint): все endpoints проверяют role через
 * {@link #requireAdmin()} - non-ADMIN authenticated user → 403
 * {@code forbidden-admin-only}, anonymous → 401 (через {@link CurrentUser}
 * резолвер на mutating endpoints). {@link CurrentUser} в {@code mapBook}
 * дополнительно нужен для получения user-id (передаётся как
 * {@code created_by} в {@code lib_books}).
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
    private final ShamelaBibliographyBackfillService backfillService;
    private final ShamelaBookDao shamelaBookDao;
    private final ShamelaAuthorDao shamelaAuthorDao;
    private final ShamelaCategoryDao shamelaCategoryDao;
    private final ShamelaSyncStateDao syncStateDao;
    private final BookRepository bookRepository;

    public ShamelaAdminController(ShamelaMasterSyncService masterSyncService,
                                  ShamelaBookImportService bookImportService,
                                  ShamelaToLibraryMapper mapper,
                                  ShamelaBibliographyBackfillService backfillService,
                                  ShamelaBookDao shamelaBookDao,
                                  ShamelaAuthorDao shamelaAuthorDao,
                                  ShamelaCategoryDao shamelaCategoryDao,
                                  ShamelaSyncStateDao syncStateDao,
                                  BookRepository bookRepository) {
        this.masterSyncService = masterSyncService;
        this.bookImportService = bookImportService;
        this.mapper = mapper;
        this.backfillService = backfillService;
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
        requireAdmin();
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
        requireAdmin();
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
        requireAdmin();
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
        requireAdmin();
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
     * Пагинированный листинг staging-каталога shamela. В отличие от
     * {@code /search} (требует {@code q}, non-paged) - возвращает ВСЕ
     * staged книги по умолчанию, чтобы admin-страница показывала каталог
     * сразу, а не пустой экран до ввода поискового запроса. {@code q}
     * опционален: при наличии - тот же name/id-матчинг что в {@code /search}
     * но paged.
     *
     * <p>Возвращает {@link PagedResponse} с обогащёнными записями
     * {@link StagingBookSearchResponse} (имя автора + флаг {@code isMapped}).
     * Сортировка детерминированная для стабильной пагинации (по id, либо
     * по релевантности+id при поиске).
     *
     * <p>Авторизация консистентна с остальными admin-endpoint этого
     * контроллера (sync-master/import-book/search) - ADMIN-only через
     * {@link #requireAdmin()} (см. class-level javadoc).
     *
     * @param page - 0-based номер страницы (default 0)
     * @param size - размер страницы (default 20, max 100 - clamp в PageRequest)
     * @param q    - опциональная подстрока для поиска по name/id
     */
    @GetMapping("/books")
    public PagedResponse<StagingBookSearchResponse> listBooks(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "q", required = false) String q) {
        requireAdmin();
        PageRequest pr = PageRequest.from(page, size);
        List<StagingBookSearchResponse> items = shamelaBookDao
                .findPage(q, pr.size(), pr.offset()).stream()
                .map(view -> new StagingBookSearchResponse(
                        view.id(),
                        view.name(),
                        view.authorName(),
                        view.majorRelease(),
                        view.isMapped()
                ))
                .toList();
        long total = shamelaBookDao.countFiltered(q);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    /**
     * Bulk-backfill academic metadata по shamela-sourced книгам через
     * {@link ShamelaBibliographyBackfillService}. Прогоняет parser по
     * текущему {@code description}, для каждого выловленного поля делает
     * {@code findOrCreate} в справочнике + UPDATE на книге. Существующие
     * non-null FK не стираются (non-destructive merge).
     *
     * <p>Используется для книг импортированных до Этапа 20.c когда parser
     * ещё не существовал. На каждый успешный sync-master + map-book
     * после 20.c новые книги получают metadata автоматически.
     *
     * <p>Синхронный endpoint - для десятков книг secунды, для тысяч
     * (после bulk-import) лучше будет async в follow-up.
     */
    @PostMapping("/backfill-bibliography")
    public BackfillBibliographyResponse backfillBibliography() {
        requireAdmin();
        ShamelaBibliographyBackfillService.BackfillResult result =
                backfillService.backfillAll();
        return new BackfillBibliographyResponse(
                result.scanned(),
                result.updated(),
                result.skipped()
        );
    }

    /**
     * Состояние shamela ETL: версия master-каталога, время последнего
     * sync, размеры staging-таблиц + сколько книг уже замаплены в
     * lib_books. Для admin dashboard на фронте.
     */
    @GetMapping("/sync-status")
    public SyncStatusResponse syncStatus() {
        requireAdmin();
        return new SyncStatusResponse(
                syncStateDao.getMasterVersion(),
                syncStateDao.getLastSyncedAt().orElse(null),
                shamelaCategoryDao.countAll(),
                shamelaAuthorDao.countAll(),
                shamelaBookDao.countAll(),
                bookRepository.countMappedFromShamela()
        );
    }

    /**
     * Гвард ADMIN-only (mirror {@code SunnahAdminController#requireAdmin}).
     * Non-ADMIN authenticated user → 403 {@code forbidden-admin-only}.
     * Anonymous traffic в dev/test (permitAll) трактуется как USER через
     * {@link SecurityContextUtils#currentRoleOrAnonymous()} (least-privilege),
     * поэтому тоже получает 403; на mutating endpoints с {@link CurrentUser}
     * anonymous отсекается раньше резолвером → 401.
     */
    private static void requireAdmin() {
        if (!UserRole.ADMIN.equals(SecurityContextUtils.currentRoleOrAnonymous())) {
            throw new AdminOnlyException(SecurityContextUtils.currentUserIdOrNull());
        }
    }

    private static void requirePositiveBookId(long bookId) {
        if (bookId < 1) {
            throw new IllegalArgumentException(
                    "bookId должен быть положительным, получено: " + bookId);
        }
    }
}
