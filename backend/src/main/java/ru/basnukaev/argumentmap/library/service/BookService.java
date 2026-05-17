package ru.basnukaev.argumentmap.library.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.domain.Muhaqqiq;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.domain.PublicationPlace;
import ru.basnukaev.argumentmap.library.domain.Publisher;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.repository.ImageRegionRepository;
import ru.basnukaev.argumentmap.library.repository.MuhaqqiqRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.repository.PublicationPlaceRepository;
import ru.basnukaev.argumentmap.library.repository.PublisherRepository;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.service.PermissionService;

@Service
public class BookService {

    public static final int DEFAULT_PAGE_RANGE = 50;

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final PageRepository pageRepository;
    private final ImageRegionRepository imageRegionRepository;
    private final AuthorityRepository authorityRepository;
    private final MuhaqqiqRepository muhaqqiqRepository;
    private final PublisherRepository publisherRepository;
    private final PublicationPlaceRepository publicationPlaceRepository;
    private final PermissionService permissionService;

    public BookService(BookRepository bookRepository,
                       ChapterRepository chapterRepository,
                       PageRepository pageRepository,
                       ImageRegionRepository imageRegionRepository,
                       AuthorityRepository authorityRepository,
                       MuhaqqiqRepository muhaqqiqRepository,
                       PublisherRepository publisherRepository,
                       PublicationPlaceRepository publicationPlaceRepository,
                       PermissionService permissionService) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.pageRepository = pageRepository;
        this.imageRegionRepository = imageRegionRepository;
        this.authorityRepository = authorityRepository;
        this.muhaqqiqRepository = muhaqqiqRepository;
        this.publisherRepository = publisherRepository;
        this.publicationPlaceRepository = publicationPlaceRepository;
        this.permissionService = permissionService;
    }

    @Transactional
    public Book createBook(BookType bookType, String title, UUID authorityId,
                           String language, String description, String metadataJson,
                           UUID currentUserId) {
        // Default PUBLIC - сохраняем поведение shamela ETL (книги
        // импортируются как open library). User-uploads через REST API
        // передают visibility явно (см. 13-args перегрузка) либо
        // приходят в PRIVATE через FileImportService.
        return createBook(bookType, title, authorityId, language, description,
                metadataJson, currentUserId,
                null, null, null, null, null, null, BookVisibility.PUBLIC);
    }

    /**
     * Создание книги с опциональными academic полями (Этап 20.e). Если
     * academic строка blank/null - FK остаётся null. Если non-blank -
     * findOrCreate в соответствующем справочнике. Integer-поля
     * сохраняются как переданы.
     *
     * <p>Используется AddSourceModal-flow когда пользователь руками
     * заводит книгу с минимальной academic-метадатой; обычный shamela
     * ETL вызывает старую перегрузку без academic.
     *
     * <p>Backward-compat перегрузка без visibility - default PUBLIC.
     */
    @Transactional
    public Book createBook(BookType bookType, String title, UUID authorityId,
                           String language, String description, String metadataJson,
                           UUID currentUserId,
                           String muhaqqiqName, String publisherName,
                           String publicationPlaceName,
                           Integer editionNumber,
                           Integer publishedYearHijri,
                           Integer publishedYearGregorian) {
        return createBook(bookType, title, authorityId, language, description,
                metadataJson, currentUserId, muhaqqiqName, publisherName,
                publicationPlaceName, editionNumber, publishedYearHijri,
                publishedYearGregorian, BookVisibility.PUBLIC);
    }

    /**
     * Создание книги с visibility (ADR-043 Amendment, Этап 22.c).
     * Используется REST endpoint POST /api/v1/library/books и
     * FileImportService.importPdf (PRIVATE по умолчанию для user-uploads).
     */
    @Transactional
    public Book createBook(BookType bookType, String title, UUID authorityId,
                           String language, String description, String metadataJson,
                           UUID currentUserId,
                           String muhaqqiqName, String publisherName,
                           String publicationPlaceName,
                           Integer editionNumber,
                           Integer publishedYearHijri,
                           Integer publishedYearGregorian,
                           String visibility) {
        if (authorityId != null && authorityRepository.findById(authorityId).isEmpty()) {
            throw new AuthorityNotFoundException(authorityId);
        }
        if (visibility == null) {
            visibility = BookVisibility.PUBLIC;
        }
        if (!BookVisibility.isValid(visibility)) {
            throw new IllegalArgumentException(
                    "Невалидное visibility: " + visibility
                            + " (ожидается PRIVATE/SHARED/PUBLIC)"
            );
        }
        UUID muhaqqiqId = resolveFk(muhaqqiqName, null, muhaqqiqRepository::findOrCreate);
        UUID publisherId = resolveFk(publisherName, null, publisherRepository::findOrCreate);
        UUID placeId = resolveFk(publicationPlaceName, null, publicationPlaceRepository::findOrCreate);

        Instant now = Instant.now();
        Book book = new Book(
                UUID.randomUUID(), bookType, title, authorityId,
                language, description, metadataJson, currentUserId,
                now, now,
                muhaqqiqId, publisherId, placeId,
                editionNumber, publishedYearHijri, publishedYearGregorian,
                visibility
        );
        bookRepository.save(book);
        return book;
    }

    @Transactional(readOnly = true)
    public List<Book> listBooks(String query, BookType type) {
        return bookRepository.findAll(query, type);
    }

    /**
     * Старая пагинация - БЕЗ visibility-фильтрации. Сохраняется для
     * internal callers / admin сценариев. REST endpoint должен
     * использовать {@link #listVisibleBooksPage}.
     */
    @Transactional(readOnly = true)
    public List<Book> listBooksPage(String query, BookType type,
                                    UUID authorityId, UUID publisherId,
                                    int limit, int offset) {
        return bookRepository.findPage(query, type, authorityId, publisherId, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countBooks(String query, BookType type,
                           UUID authorityId, UUID publisherId) {
        return bookRepository.countFiltered(query, type, authorityId, publisherId);
    }

    /**
     * Пагинированный список книг видимых пользователю (ADR-043 Amendment).
     * ADMIN получает все книги без visibility-фильтра.
     */
    @Transactional(readOnly = true)
    public List<Book> listVisibleBooksPage(UUID userId, String role,
                                           String query, BookType type,
                                           UUID authorityId, UUID publisherId,
                                           int limit, int offset) {
        if (ru.basnukaev.argumentmap.auth.domain.UserRole.ADMIN.equals(role)) {
            return bookRepository.findPage(query, type, authorityId, publisherId, limit, offset);
        }
        return bookRepository.findVisibleToUserPage(userId, query, type, authorityId, publisherId,
                limit, offset);
    }

    @Transactional(readOnly = true)
    public long countVisibleBooks(UUID userId, String role,
                                  String query, BookType type,
                                  UUID authorityId, UUID publisherId) {
        if (ru.basnukaev.argumentmap.auth.domain.UserRole.ADMIN.equals(role)) {
            return bookRepository.countFiltered(query, type, authorityId, publisherId);
        }
        return bookRepository.countVisibleToUser(userId, query, type, authorityId, publisherId);
    }

    /**
     * Backward-compat для internal callers без permission check
     * (admin tooling, scheduled jobs). REST endpoint должен использовать
     * {@link #getBookWithChapters(UUID, UUID, String)}.
     */
    @Transactional(readOnly = true)
    public BookDetail getBookWithChapters(UUID bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        List<Chapter> flat = chapterRepository.findByBookId(bookId);
        List<ChapterNode> tree = buildChapterTree(flat);
        Authority authority = book.authorityId() != null
                ? authorityRepository.findById(book.authorityId()).orElse(null)
                : null;
        Muhaqqiq muhaqqiq = book.muhaqqiqId() != null
                ? muhaqqiqRepository.findById(book.muhaqqiqId()).orElse(null)
                : null;
        Publisher publisher = book.publisherId() != null
                ? publisherRepository.findById(book.publisherId()).orElse(null)
                : null;
        PublicationPlace place = book.publicationPlaceId() != null
                ? publicationPlaceRepository.findById(book.publicationPlaceId()).orElse(null)
                : null;
        return new BookDetail(book, tree, authority, muhaqqiq, publisher, place);
    }

    /**
     * Версия с permission check (ADR-043 Amendment). Используется REST.
     */
    @Transactional(readOnly = true)
    public BookDetail getBookWithChapters(UUID bookId, UUID userId, String role) {
        permissionService.assertCanReadBook(bookId, userId, role);
        return getBookWithChapters(bookId);
    }

    /**
     * Backward-compat без permission check.
     */
    @Transactional
    public void deleteBook(UUID bookId) {
        boolean removed = bookRepository.deleteById(bookId);
        if (!removed) {
            throw new BookNotFoundException(bookId);
        }
    }

    /**
     * Удаление книги - только owner (или ADMIN). EDITOR этого не может,
     * даже на SHARED. См. ADR-043 Amendment матрицу.
     */
    @Transactional
    public void deleteBook(UUID bookId, UUID userId, String role) {
        bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        permissionService.assertIsBookOwner(bookId, userId, role);
        bookRepository.deleteById(bookId);
    }

    /**
     * Меняет visibility книги (ADR-043 Amendment) - только owner.
     * EDITOR не может (privilege-escalation).
     */
    @Transactional
    public Book updateVisibility(UUID bookId, String newVisibility,
                                 UUID userId, String role) {
        if (!BookVisibility.isValid(newVisibility)) {
            throw new IllegalArgumentException(
                    "Невалидное visibility: " + newVisibility
            );
        }
        bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        permissionService.assertIsBookOwner(bookId, userId, role);
        bookRepository.updateVisibility(bookId, newVisibility);
        return bookRepository.findById(bookId).orElseThrow();
    }

    /**
     * Partial update academic metadata (Этап 20.d, BookEditModal).
     *
     * <p>Для имён ({@code muhaqqiq}/{@code publisher}/{@code publicationPlace}):
     * {@code null} = no change, blank string = clear FK to null, non-blank =
     * {@code findOrCreate(name.trim())} в соответствующем справочнике.
     *
     * <p>Для целочисленных полей ({@code editionNumber}/{@code
     * publishedYearHijri}/{@code publishedYearGregorian}): {@code null} =
     * no change, value = replace.
     *
     * @return обновлённая книга (для возврата в response)
     */
    @Transactional
    public Book updateAcademicMetadata(UUID bookId,
                                       String muhaqqiqName,
                                       String publisherName,
                                       String publicationPlaceName,
                                       Integer editionNumber,
                                       Integer publishedYearHijri,
                                       Integer publishedYearGregorian) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        UUID newMuhaqqiqId = resolveFk(muhaqqiqName, book.muhaqqiqId(),
                muhaqqiqRepository::findOrCreate);
        UUID newPublisherId = resolveFk(publisherName, book.publisherId(),
                publisherRepository::findOrCreate);
        UUID newPlaceId = resolveFk(publicationPlaceName, book.publicationPlaceId(),
                publicationPlaceRepository::findOrCreate);

        Integer newEdition = editionNumber != null ? editionNumber : book.editionNumber();
        Integer newHijri = publishedYearHijri != null
                ? publishedYearHijri : book.publishedYearHijri();
        Integer newGregorian = publishedYearGregorian != null
                ? publishedYearGregorian : book.publishedYearGregorian();

        bookRepository.updateAcademicMetadata(
                bookId, newMuhaqqiqId, newPublisherId, newPlaceId,
                newEdition, newHijri, newGregorian
        );
        return bookRepository.findById(bookId).orElseThrow();
    }

    /**
     * Версия updateAcademicMetadata с permission check (ADR-043 Amendment).
     * Owner и EDITOR могут update; MEMBER нет. PRIVATE - только owner.
     */
    @Transactional
    public Book updateAcademicMetadata(UUID bookId,
                                       String muhaqqiqName,
                                       String publisherName,
                                       String publicationPlaceName,
                                       Integer editionNumber,
                                       Integer publishedYearHijri,
                                       Integer publishedYearGregorian,
                                       UUID userId, String role) {
        permissionService.assertCanWriteBook(bookId, userId, role);
        return updateAcademicMetadata(bookId, muhaqqiqName, publisherName,
                publicationPlaceName, editionNumber, publishedYearHijri,
                publishedYearGregorian);
    }

    private static UUID resolveFk(String name, UUID currentFk,
                                  java.util.function.Function<String, UUID> findOrCreate) {
        if (name == null) {
            return currentFk;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return findOrCreate.apply(trimmed);
    }

    /**
     * Список страниц книги. Если {@code fromPage}/{@code toPage} не
     * указаны - возвращает все страницы книги (без ограничений).
     * Для больших книг (Сахих аль-Бухари ~11208 страниц) это
     * {@code PageSummaryResponse[]} ~900KB JSON - терпимо для одного запроса
     * на init reader'а.
     *
     * <p>Если из практики окажется что initial-load слишком медленный
     * на больших книгах - оптимизировать через lazy paging window
     * (фронт грузит summary только в окне ±N страниц от текущей).
     * На MVP - все за один раз для простоты.
     */
    @Transactional(readOnly = true)
    public List<Page> listPages(UUID bookId, Integer fromPage, Integer toPage) {
        if (bookRepository.findById(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        int from = fromPage == null ? 1 : fromPage;
        int to = toPage == null ? Integer.MAX_VALUE : toPage;
        return pageRepository.findByBookIdRange(bookId, from, to);
    }

    @Transactional(readOnly = true)
    public PageDetail getPage(UUID pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));
        return new PageDetail(page, imageRegionRepository.findByPageId(pageId));
    }

    /**
     * Сохранение ProseMirror JSON для страницы (Этап 17.0, ADR-039).
     * Trust frontend Tiptap-сериализатор - schema validation на
     * application level не делаем (см. ADR-039 «backend lookup без
     * структурной валидации - принимает любой JSON»).
     *
     * <p>Updates только {@code formatted_content} + {@code updated_at};
     * остальные поля страницы не трогаются. Возвращает обновлённую
     * страницу с image regions для consistency с {@code getPage}.
     *
     * @throws PageNotFoundException если page id не найден
     */
    @Transactional
    public PageDetail updateFormattedContent(UUID pageId, String formattedContentJson) {
        boolean updated = pageRepository.updateFormattedContent(pageId, formattedContentJson);
        if (!updated) {
            throw new PageNotFoundException(pageId);
        }
        return getPage(pageId);
    }

    /**
     * Собирает плоский список глав в дерево по parent_chapter_id.
     * Корневые главы — те, у которых parentChapterId == null.
     * Сортировка по orderIndex обеспечивается порядком из репозитория
     * (findByBookId ORDER BY parent_chapter_id NULLS FIRST, order_index).
     */
    private List<ChapterNode> buildChapterTree(List<Chapter> flat) {
        Map<UUID, ChapterNode> nodesById = new LinkedHashMap<>();
        for (Chapter chapter : flat) {
            nodesById.put(chapter.id(), new ChapterNode(chapter, new ArrayList<>()));
        }

        List<ChapterNode> roots = new ArrayList<>();
        for (Chapter chapter : flat) {
            ChapterNode node = nodesById.get(chapter.id());
            if (chapter.parentChapterId() == null) {
                roots.add(node);
            } else {
                ChapterNode parent = nodesById.get(chapter.parentChapterId());
                if (parent != null) {
                    parent.children().add(node);
                }
            }
        }
        return roots;
    }
}
