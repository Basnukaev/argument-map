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

    public BookService(BookRepository bookRepository,
                       ChapterRepository chapterRepository,
                       PageRepository pageRepository,
                       ImageRegionRepository imageRegionRepository,
                       AuthorityRepository authorityRepository,
                       MuhaqqiqRepository muhaqqiqRepository,
                       PublisherRepository publisherRepository,
                       PublicationPlaceRepository publicationPlaceRepository) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.pageRepository = pageRepository;
        this.imageRegionRepository = imageRegionRepository;
        this.authorityRepository = authorityRepository;
        this.muhaqqiqRepository = muhaqqiqRepository;
        this.publisherRepository = publisherRepository;
        this.publicationPlaceRepository = publicationPlaceRepository;
    }

    @Transactional
    public Book createBook(BookType bookType, String title, UUID authorityId,
                           String language, String description, String metadataJson,
                           UUID currentUserId) {
        if (authorityId != null && authorityRepository.findById(authorityId).isEmpty()) {
            throw new AuthorityNotFoundException(authorityId);
        }
        Instant now = Instant.now();
        Book book = new Book(
                UUID.randomUUID(), bookType, title, authorityId,
                language, description, metadataJson, currentUserId,
                now, now,
                null, null, null, null, null, null
        );
        bookRepository.save(book);
        return book;
    }

    @Transactional(readOnly = true)
    public List<Book> listBooks(String query, BookType type) {
        return bookRepository.findAll(query, type);
    }

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

    @Transactional
    public void deleteBook(UUID bookId) {
        boolean removed = bookRepository.deleteById(bookId);
        if (!removed) {
            throw new BookNotFoundException(bookId);
        }
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
