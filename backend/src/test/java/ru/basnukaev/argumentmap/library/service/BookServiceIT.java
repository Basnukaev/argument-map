package ru.basnukaev.argumentmap.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.exception.AuthorityNotFoundException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.repository.ImageRegionRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BookServiceIT {

    @Autowired
    private BookService bookService;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ImageRegionRepository imageRegionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    @Test
    void createBook_happyPath_persistsAndReturnsBook() {
        Authority author = saveAuthor("Ибн Таймийя");

        Book created = bookService.createBook(
                BookType.BOOK, "Иктида ас-сырат аль-мустакым",
                author.id(), "ar", "трактат", null, userId
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.bookType()).isEqualTo(BookType.BOOK);
        assertThat(created.authorityId()).isEqualTo(author.id());
        assertThat(created.createdBy()).isEqualTo(userId);
        assertThat(created.createdAt()).isEqualTo(created.updatedAt());
    }

    @Test
    void createBook_quranWithoutAuthor_persistsWithNullAuthority() {
        Book quran = bookService.createBook(
                BookType.QURAN, "Коран", null, "ar", null, null, userId
        );

        assertThat(quran.authorityId()).isNull();
    }

    @Test
    void createBook_invalidAuthorityId_throwsAuthorityNotFound() {
        UUID nonexistent = UUID.randomUUID();

        assertThatThrownBy(() -> bookService.createBook(
                BookType.BOOK, "T", nonexistent, "ar", null, null, userId
        )).isInstanceOf(AuthorityNotFoundException.class);
    }

    @Test
    void listBooks_filterByQuery_returnsMatching() {
        bookService.createBook(BookType.BOOK, "Иктида ас-сырат", null, "ar", null, null, userId);
        bookService.createBook(BookType.BOOK, "Хусн аль-максыд", null, "ar", null, null, userId);

        List<Book> found = bookService.listBooks("ИКТИДА", null);

        assertThat(found).hasSize(1);
    }

    @Test
    void listBooks_filterByType_returnsOnlyMatchingType() {
        bookService.createBook(BookType.QURAN, "Коран", null, "ar", null, null, userId);
        bookService.createBook(BookType.HADITH_COLLECTION, "Сахих аль-Бухари", null, "ar", null, null, userId);

        List<Book> hadiths = bookService.listBooks(null, BookType.HADITH_COLLECTION);

        assertThat(hadiths).hasSize(1);
        assertThat(hadiths.get(0).bookType()).isEqualTo(BookType.HADITH_COLLECTION);
    }

    @Test
    void getBookWithChapters_buildsTwoLevelTree() {
        Book book = bookService.createBook(BookType.BOOK, "T", null, "ar", null, null, userId);
        Chapter root1 = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Том 1", 0, null, Instant.now()
        ));
        Chapter root2 = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Том 2", 1, null, Instant.now()
        ));
        chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), root1.id(), "Глава 1.1", 0, null, Instant.now()
        ));
        chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), root1.id(), "Глава 1.2", 1, null, Instant.now()
        ));

        BookDetail detail = bookService.getBookWithChapters(book.id());

        assertThat(detail.book().id()).isEqualTo(book.id());
        assertThat(detail.rootChapters()).hasSize(2);
        assertThat(detail.rootChapters().get(0).chapter().id()).isEqualTo(root1.id());
        assertThat(detail.rootChapters().get(0).children()).hasSize(2);
        assertThat(detail.rootChapters().get(1).chapter().id()).isEqualTo(root2.id());
        assertThat(detail.rootChapters().get(1).children()).isEmpty();
    }

    @Test
    void getBookWithChapters_bookWithoutChapters_returnsEmptyTree() {
        Book book = bookService.createBook(BookType.BOOK, "T", null, "ar", null, null, userId);

        BookDetail detail = bookService.getBookWithChapters(book.id());

        assertThat(detail.rootChapters()).isEmpty();
    }

    @Test
    void getBookWithChapters_nonexistent_throwsBookNotFound() {
        assertThatThrownBy(() -> bookService.getBookWithChapters(UUID.randomUUID()))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void deleteBook_happyPath_removesBookAndCascades() {
        Book book = bookService.createBook(BookType.BOOK, "T", null, "ar", null, null, userId);
        Chapter chapter = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Глава", 0, null, Instant.now()
        ));

        bookService.deleteBook(book.id());

        assertThat(chapterRepository.findById(chapter.id())).isEmpty();
        assertThatThrownBy(() -> bookService.getBookWithChapters(book.id()))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void deleteBook_nonexistent_throwsBookNotFound() {
        assertThatThrownBy(() -> bookService.deleteBook(UUID.randomUUID()))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void listPages_returnsRangeInOrder() {
        Book book = bookService.createBook(BookType.BOOK, "T", null, "ar", null, null, userId);
        for (int i = 1; i <= 10; i++) {
            pageRepository.save(new Page(
                    UUID.randomUUID(), book.id(), null, i,
                    null, null, null,
                    "p" + i, null, Instant.now(), Instant.now()
            ));
        }

        List<Page> range = bookService.listPages(book.id(), 3, 6);

        assertThat(range).extracting(Page::pageNumber).containsExactly(3, 4, 5, 6);
    }

    @Test
    void listPages_returnsAllPages_whenRangeNotProvided() {
        // Раньше был default 1..50 - но это обрезало большие книги
        // (Сахих аль-Бухари 11208 стр) до первой "пачки". Теперь без
        // явного range возвращаем все страницы книги
        Book book = bookService.createBook(BookType.BOOK, "T", null, "ar", null, null, userId);
        for (int i = 1; i <= 60; i++) {
            pageRepository.save(new Page(
                    UUID.randomUUID(), book.id(), null, i,
                    null, null, null,
                    "p", null, Instant.now(), Instant.now()
            ));
        }

        List<Page> all = bookService.listPages(book.id(), null, null);

        assertThat(all).hasSize(60);
        assertThat(all.get(0).pageNumber()).isOne();
        assertThat(all.get(59).pageNumber()).isEqualTo(60);
    }

    @Test
    void listPages_nonexistentBook_throwsBookNotFound() {
        assertThatThrownBy(() -> bookService.listPages(UUID.randomUUID(), null, null))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void getPage_returnsPageWithRegions() {
        Book book = bookService.createBook(BookType.MANUSCRIPT, "T", null, "ar", null, null, userId);
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                null, "https://x/scan.jpg", Instant.now(), Instant.now()
        ));
        imageRegionRepository.save(new ImageRegion(
                UUID.randomUUID(), page.id(), 0.1, 0.1, 0.5, 0.5, "بسم الله", Instant.now()
        ));

        PageDetail detail = bookService.getPage(page.id());

        assertThat(detail.page().id()).isEqualTo(page.id());
        assertThat(detail.regions()).hasSize(1);
        assertThat(detail.regions().get(0).extractedText()).isEqualTo("بسم الله");
    }

    @Test
    void getPage_nonexistent_throwsPageNotFound() {
        assertThatThrownBy(() -> bookService.getPage(UUID.randomUUID()))
                .isInstanceOf(PageNotFoundException.class);
    }

    private Authority saveAuthor(String name) {
        return authorityRepository.save(new Authority(
                UUID.randomUUID(), name, null, null, null, null, Instant.now(),
                null, null
        ));
    }
}
