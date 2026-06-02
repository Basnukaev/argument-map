package ru.basnukaev.argumentmap.library.shamela.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.shamela.api.ShamelaApiException;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaAuthorRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaBookRow;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaCategoryRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaAuthorDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaBookDao;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaCategoryDao;
import ru.basnukaev.argumentmap.library.shamela.service.BookImportResult;
import ru.basnukaev.argumentmap.library.shamela.service.MappedBookResult;
import ru.basnukaev.argumentmap.library.shamela.service.MasterSyncResult;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaBookImportService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaMasterSyncService;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaNotFoundException;
import ru.basnukaev.argumentmap.library.shamela.service.ShamelaToLibraryMapper;

/**
 * MockMvc IT для {@code /api/v1/admin/shamela/*}. Сервисный слой замокан
 * через {@code @MockitoBean} - этот тест проверяет только тонкий
 * controller-слой: HTTP-маппинг, validation, exception → ProblemDetail.
 *
 * <p>Полный pipeline-тест с реальным postgres - в
 * {@code ShamelaImportServiceIT} (master sync + book import) и
 * {@code ShamelaToLibraryMapperIT}, дублировать тут смысла нет.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShamelaAdminControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShamelaCategoryDao categoryDao;

    @Autowired
    private ShamelaAuthorDao authorDao;

    @Autowired
    private ShamelaBookDao bookDao;

    @Autowired
    private BookRepository bookRepository;

    @MockitoBean
    private ShamelaMasterSyncService masterSyncService;

    @MockitoBean
    private ShamelaBookImportService bookImportService;

    @MockitoBean
    private ShamelaToLibraryMapper mapper;

    // Named test data constants (T-07 audit). Магические значения превращены
    // в semantic-имена - читателю сразу ясно интент тестового сценария.
    private static final long BOOK_ID_SAHIH_AL_BUKHARI = 41557L;
    private static final long BOOK_ID_NOT_FOUND = 99999L;
    private static final long BOOK_ID_AL_BUKHARI_AL_SAGHIR = 41558L;
    private static final String SEARCH_QUERY_BUKHARI = "1681";

    private UUID testUserId;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM lib_image_regions");
        jdbcTemplate.update("DELETE FROM lib_pages");
        jdbcTemplate.update("DELETE FROM lib_chapters");
        jdbcTemplate.update("DELETE FROM lib_books");
        jdbcTemplate.update("DELETE FROM lib_shamela_page");
        jdbcTemplate.update("DELETE FROM lib_shamela_title");
        jdbcTemplate.update("DELETE FROM lib_shamela_book");
        jdbcTemplate.update("DELETE FROM lib_shamela_author");
        jdbcTemplate.update("DELETE FROM lib_shamela_category");
        jdbcTemplate.update(
                "UPDATE lib_shamela_sync_state SET master_version = 0, last_synced_at = NULL WHERE id = 1");
        testUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                testUserId, "admin-it-" + testUserId, testUserId + "@test.local"
        );
    }

    // ---------------- sync-master ----------------

    @Test
    void syncMaster_returns_200_with_body_on_success() throws Exception {
        when(masterSyncService.syncMaster()).thenReturn(
                MasterSyncResult.synced(0, 1261, 50, 25_000, 8500));

        mockMvc.perform(post("/api/v1/admin/shamela/sync-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.previousVersion").value(0))
                .andExpect(jsonPath("$.currentVersion").value(1261))
                .andExpect(jsonPath("$.categoriesCount").value(50))
                .andExpect(jsonPath("$.authorsCount").value(25_000))
                .andExpect(jsonPath("$.booksCount").value(8500));
    }

    @Test
    void syncMaster_returns_200_with_unchanged_when_version_same() throws Exception {
        when(masterSyncService.syncMaster()).thenReturn(MasterSyncResult.unchanged(1261));

        mockMvc.perform(post("/api/v1/admin/shamela/sync-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.currentVersion").value(1261))
                .andExpect(jsonPath("$.booksCount").value(0));
    }

    @Test
    void syncMaster_returns_502_on_shamela_api_error() throws Exception {
        when(masterSyncService.syncMaster()).thenThrow(
                new ShamelaApiException("HTTP 503 от dev.shamela.ws"));

        mockMvc.perform(post("/api/v1/admin/shamela/sync-master"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("shamela API недоступна"))
                .andExpect(jsonPath("$.detail").value("HTTP 503 от dev.shamela.ws"));
    }

    // ---------------- import-book ----------------

    @Test
    void importBook_returns_200_with_body_on_success() throws Exception {
        when(bookImportService.importBook(BOOK_ID_SAHIH_AL_BUKHARI))
                .thenReturn(new BookImportResult(BOOK_ID_SAHIH_AL_BUKHARI, 4, 320, 18));

        mockMvc.perform(post("/api/v1/admin/shamela/import-book/41557"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(41557))
                .andExpect(jsonPath("$.majorRelease").value(4))
                .andExpect(jsonPath("$.pagesCount").value(320))
                .andExpect(jsonPath("$.titlesCount").value(18));

        verify(bookImportService).importBook(BOOK_ID_SAHIH_AL_BUKHARI);
    }

    @Test
    void importBook_returns_404_when_book_missing_in_staging() throws Exception {
        when(bookImportService.importBook(BOOK_ID_NOT_FOUND)).thenThrow(
                new ShamelaNotFoundException("книга id=99999 не найдена"));

        mockMvc.perform(post("/api/v1/admin/shamela/import-book/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Запись shamela не найдена"))
                .andExpect(jsonPath("$.detail").value("книга id=99999 не найдена"));
    }

    @Test
    void importBook_returns_400_on_non_positive_id() throws Exception {
        // T-06 audit: 1 happy + 1 error per validation case. Покрываем
        // -1 и 0 в одном тесте - requirePositiveBookId одинаково обрабатывает
        mockMvc.perform(post("/api/v1/admin/shamela/import-book/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный аргумент"));
        mockMvc.perform(post("/api/v1/admin/shamela/import-book/0"))
                .andExpect(status().isBadRequest());

        verify(bookImportService, never()).importBook(anyLong());
    }

    // ---------------- map-book ----------------

    @Test
    void mapBook_returns_200_with_body_on_success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID authorityUuid = UUID.randomUUID();
        when(mapper.mapBook(eq(BOOK_ID_SAHIH_AL_BUKHARI), eq(userId)))
                .thenReturn(MappedBookResult.freshlyCreated(
                        bookUuid, BOOK_ID_SAHIH_AL_BUKHARI, authorityUuid, 18, 320));

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/41557")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookUuid.toString()))
                .andExpect(jsonPath("$.shamelaBookId").value(41557))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.authorityId").value(authorityUuid.toString()))
                .andExpect(jsonPath("$.chaptersCount").value(18))
                .andExpect(jsonPath("$.pagesCount").value(320));
    }

    @Test
    void mapBook_returns_already_mapped_with_zero_counts() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID authorityUuid = UUID.randomUUID();
        when(mapper.mapBook(eq(BOOK_ID_SAHIH_AL_BUKHARI), eq(userId)))
                .thenReturn(MappedBookResult.alreadyMapped(bookUuid, BOOK_ID_SAHIH_AL_BUKHARI, authorityUuid));

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/41557")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.chaptersCount").value(0))
                .andExpect(jsonPath("$.pagesCount").value(0));
    }

    @Test
    void mapBook_returns_401_when_x_user_id_header_missing() throws Exception {
        // ADR-040 + b9da308: anonymous principal → @CurrentUser резолвер
        // бросает InvalidTokenException → 401 invalid-token (frontend
        // refresh-on-401 interceptor trigger)
        mockMvc.perform(post("/api/v1/admin/shamela/map-book/41557"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value(
                        "Невалидный или истёкший токен"));

        verifyNoInteractions(mapper);
    }

    @Test
    void mapBook_returns_404_when_book_missing() throws Exception {
        UUID userId = UUID.randomUUID();
        when(mapper.mapBook(eq(BOOK_ID_NOT_FOUND), eq(userId)))
                .thenThrow(new ShamelaNotFoundException(
                        "shamela book id=99999 не найдена в lib_shamela_book"));

        mockMvc.perform(post("/api/v1/admin/shamela/map-book/99999")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Запись shamela не найдена"));
    }

    // T-06 audit: requirePositiveBookId покрыт importBook_returns_400_on_non_positive_id.
    // Не дублируем тот же validation case для mapBook - shared helper.

    // ---------------- search ----------------

    @Test
    void searchBooks_returns_results_with_author_and_mapped_flag() throws Exception {
        // seed: автор "Аль-Бухари", две книги "صحيح البخاري" и "البخاري الصغير",
        // первая уже замаплена в lib_books
        authorDao.upsertAll(java.util.List.of(
                new ShamelaAuthorRow(100L, "Аль-Бухари", "имам", 256, false),
                new ShamelaAuthorRow(101L, "Муслим", null, 261, false)
        ));
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(BOOK_ID_SAHIH_AL_BUKHARI, "صحيح البخاري", null, 100L, null, null, null,
                        4, 0, null, null, null, null, false),
                new ShamelaBookRow(BOOK_ID_AL_BUKHARI_AL_SAGHIR, "البخاري الصغير", null, 100L, null, null, null,
                        2, 0, null, null, null, null, false),
                new ShamelaBookRow(41559L, "صحيح مسلم", null, 101L, null, null, null,
                        3, 0, null, null, null, null, false)
        ));
        // 41557 уже замаплена в lib_books
        bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "صحيح البخاري", null, "ar",
                null, "{\"shamela_book_id\":41557}", testUserId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));

        mockMvc.perform(get("/api/v1/admin/shamela/search").param("q", "البخاري"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // первый результат - 41557 (точное совпадение по подстроке + замаплен)
                .andExpect(jsonPath("$[0].bookId").value(41557))
                .andExpect(jsonPath("$[0].authorName").value("Аль-Бухари"))
                .andExpect(jsonPath("$[0].majorRelease").value(4))
                .andExpect(jsonPath("$[0].isMapped").value(true))
                // второй - 41558
                .andExpect(jsonPath("$[1].bookId").value(41558))
                .andExpect(jsonPath("$[1].isMapped").value(false));
    }

    @Test
    void searchBooks_excludes_tombstoned_records() throws Exception {
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1L, "живая книга", null, null, null, null, null,
                        1, 0, null, null, null, null, false),
                new ShamelaBookRow(2L, "удалённая книга", null, null, null, null, null,
                        1, 0, null, null, null, null, true)
        ));

        mockMvc.perform(get("/api/v1/admin/shamela/search").param("q", "книга"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookId").value(1));
    }

    @Test
    void searchBooks_respects_limit_param() throws Exception {
        for (int i = 1; i <= 30; i++) {
            bookDao.upsertAll(java.util.List.of(new ShamelaBookRow(
                    (long) i, "test книга " + i, null, null, null, null, null,
                    1, 0, null, null, null, null, false
            )));
        }

        mockMvc.perform(get("/api/v1/admin/shamela/search").param("q", "test").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void searchBooks_returns_400_when_q_blank() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shamela/search").param("q", ""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/shamela/search").param("q", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBooks_finds_by_exact_id() throws Exception {
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1681L, "صحيح البخاري - ط السلطانية", null, null, null, null, null,
                        6, 0, null, null, null, null, false),
                new ShamelaBookRow(2L, "случайная книга", null, null, null, null, null,
                        1, 0, null, null, null, null, false)
        ));

        // поиск по числу-id находит точное совпадение и кладёт первым
        mockMvc.perform(get("/api/v1/admin/shamela/search").param("q", "1681"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookId").value(1681))
                .andExpect(jsonPath("$[0].majorRelease").value(6));
    }

    // ---------------- books (paged listing) ----------------

    @Test
    void books_noQuery_returnsAllPaged() throws Exception {
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1L, "книга один", null, null, null, null, null,
                        1, 0, null, null, null, null, false),
                new ShamelaBookRow(2L, "книга два", null, null, null, null, null,
                        1, 0, null, null, null, null, false),
                new ShamelaBookRow(3L, "книга три", null, null, null, null, null,
                        1, 0, null, null, null, null, false)
        ));

        mockMvc.perform(get("/api/v1/admin/shamela/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                // детерминированный порядок по id при пустом q
                .andExpect(jsonPath("$.items[0].bookId").value(1))
                .andExpect(jsonPath("$.items[1].bookId").value(2))
                .andExpect(jsonPath("$.items[2].bookId").value(3));
    }

    @Test
    void books_noQuery_excludesTombstoned() throws Exception {
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1L, "живая", null, null, null, null, null,
                        1, 0, null, null, null, null, false),
                new ShamelaBookRow(2L, "удалённая", null, null, null, null, null,
                        1, 0, null, null, null, null, true)
        ));

        mockMvc.perform(get("/api/v1/admin/shamela/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].bookId").value(1));
    }

    @Test
    void books_withQuery_filters() throws Exception {
        authorDao.upsertAll(java.util.List.of(
                new ShamelaAuthorRow(100L, "Аль-Бухари", "имам", 256, false)
        ));
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(BOOK_ID_SAHIH_AL_BUKHARI, "صحيح البخاري", null, 100L, null, null, null,
                        4, 0, null, null, null, null, false),
                new ShamelaBookRow(41559L, "صحيح مسلم", null, null, null, null, null,
                        3, 0, null, null, null, null, false)
        ));

        mockMvc.perform(get("/api/v1/admin/shamela/books").param("q", "البخاري"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].bookId").value(41557))
                .andExpect(jsonPath("$.items[0].authorName").value("Аль-Бухари"))
                .andExpect(jsonPath("$.items[0].majorRelease").value(4));
    }

    @Test
    void books_withQuery_findsByExactId() throws Exception {
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1681L, "صحيح البخاري - ط السلطانية", null, null, null, null, null,
                        6, 0, null, null, null, null, false),
                new ShamelaBookRow(2L, "случайная книга", null, null, null, null, null,
                        1, 0, null, null, null, null, false)
        ));

        mockMvc.perform(get("/api/v1/admin/shamela/books").param("q", "1681"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].bookId").value(1681));
    }

    @Test
    void books_paginated_page1() throws Exception {
        for (int i = 1; i <= 5; i++) {
            bookDao.upsertAll(java.util.List.of(new ShamelaBookRow(
                    (long) i, "книга " + i, null, null, null, null, null,
                    1, 0, null, null, null, null, false
            )));
        }

        // page 0, size 2 → первые 2 по id (1, 2)
        mockMvc.perform(get("/api/v1/admin/shamela/books")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrev").value(false))
                .andExpect(jsonPath("$.items[0].bookId").value(1))
                .andExpect(jsonPath("$.items[1].bookId").value(2));

        // page 1, size 2 → следующие 2 (3, 4) - стабильная пагинация
        mockMvc.perform(get("/api/v1/admin/shamela/books")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasPrev").value(true))
                .andExpect(jsonPath("$.items[0].bookId").value(3))
                .andExpect(jsonPath("$.items[1].bookId").value(4));
    }

    @Test
    void books_sizeOverMax_clampsTo100() throws Exception {
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1L, "книга", null, null, null, null, null,
                        1, 0, null, null, null, null, false)
        ));

        mockMvc.perform(get("/api/v1/admin/shamela/books").param("size", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void books_includesMappedFlagAndAuthor() throws Exception {
        authorDao.upsertAll(java.util.List.of(
                new ShamelaAuthorRow(100L, "Аль-Бухари", "имам", 256, false)
        ));
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(BOOK_ID_SAHIH_AL_BUKHARI, "صحيح البخاري", null, 100L, null, null, null,
                        4, 0, null, null, null, null, false)
        ));
        bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "صحيح البخاري", null, "ar",
                null, "{\"shamela_book_id\":41557}", testUserId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));

        mockMvc.perform(get("/api/v1/admin/shamela/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].bookId").value(41557))
                .andExpect(jsonPath("$.items[0].authorName").value("Аль-Бухари"))
                .andExpect(jsonPath("$.items[0].isMapped").value(true));
    }

    @Test
    void books_emptyDb_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shamela/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    // ---------------- sync-status ----------------

    @Test
    void syncStatus_returns_initial_state_for_empty_db() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shamela/sync-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterVersion").value(0))
                .andExpect(jsonPath("$.lastSyncedAt").doesNotExist())
                .andExpect(jsonPath("$.categoriesCount").value(0))
                .andExpect(jsonPath("$.authorsCount").value(0))
                .andExpect(jsonPath("$.booksCount").value(0))
                .andExpect(jsonPath("$.mappedBooksCount").value(0));
    }

    @Test
    void syncStatus_reflects_staging_and_mapped_counts() throws Exception {
        categoryDao.upsertAll(java.util.List.of(
                new ShamelaCategoryRow(1L, "Хадис", 1, false),
                new ShamelaCategoryRow(2L, "Фикх", 2, false)
        ));
        authorDao.upsertAll(java.util.List.of(
                new ShamelaAuthorRow(100L, "А", null, null, false),
                new ShamelaAuthorRow(101L, "Б", null, null, false),
                new ShamelaAuthorRow(102L, "В", null, null, false)
        ));
        bookDao.upsertAll(java.util.List.of(
                new ShamelaBookRow(1L, "к1", null, 100L, null, null, null,
                        1, 0, null, null, null, null, false),
                new ShamelaBookRow(2L, "к2", null, 101L, null, null, null,
                        1, 0, null, null, null, null, false)
        ));
        bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "замапленная", null, "ar",
                null, "{\"shamela_book_id\":1}", testUserId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));
        jdbcTemplate.update(
                "UPDATE lib_shamela_sync_state SET master_version = 1261, last_synced_at = now() WHERE id = 1");

        mockMvc.perform(get("/api/v1/admin/shamela/sync-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterVersion").value(1261))
                .andExpect(jsonPath("$.lastSyncedAt").exists())
                .andExpect(jsonPath("$.categoriesCount").value(2))
                .andExpect(jsonPath("$.authorsCount").value(3))
                .andExpect(jsonPath("$.booksCount").value(2))
                .andExpect(jsonPath("$.mappedBooksCount").value(1));
    }
}
