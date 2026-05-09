package ru.basnukaev.argumentmap.library.repository;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.domain.Page;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PageRepositoryIT {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private Book book;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        Instant now = Instant.now();
        book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "T", null, "ar",
                null, null, userId, now, now
        ));
    }

    @Test
    void save_textOnlyPage_persistsCorrectly() {
        Page page = new Page(
                UUID.randomUUID(), book.id(), null, 1,
                "بسم الله", null, Instant.now(), Instant.now()
        );

        pageRepository.save(page);

        Page reloaded = pageRepository.findById(page.id()).orElseThrow();
        assertThat(reloaded.textContent()).isEqualTo("بسم الله");
        assertThat(reloaded.imageUrl()).isNull();
    }

    @Test
    void save_imageOnlyPage_persistsCorrectly() {
        Page page = new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, "https://example.com/scan-1.jpg", Instant.now(), Instant.now()
        );

        pageRepository.save(page);

        Page reloaded = pageRepository.findById(page.id()).orElseThrow();
        assertThat(reloaded.textContent()).isNull();
        assertThat(reloaded.imageUrl()).isEqualTo("https://example.com/scan-1.jpg");
    }

    @Test
    void save_emptyPage_violatesContentPresentCheck() {
        Page page = new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> pageRepository.save(page))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lib_pages_content_present");
    }

    @Test
    void save_duplicatePageNumberInBook_violatesUniqueConstraint() {
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 5,
                "first", null, Instant.now(), Instant.now()
        ));

        Page duplicate = new Page(
                UUID.randomUUID(), book.id(), null, 5,
                "second", null, Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> pageRepository.save(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByBookIdRange_returnsPagesInOrder() {
        for (int i = 1; i <= 10; i++) {
            pageRepository.save(new Page(
                    UUID.randomUUID(), book.id(), null, i,
                    "page " + i, null, Instant.now(), Instant.now()
            ));
        }

        List<Page> range = pageRepository.findByBookIdRange(book.id(), 3, 6);

        assertThat(range).extracting(Page::pageNumber).containsExactly(3, 4, 5, 6);
    }

    @Test
    void deleteBook_cascadesPages() {
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                "x", null, Instant.now(), Instant.now()
        ));

        bookRepository.deleteById(book.id());

        assertThat(pageRepository.findById(page.id())).isEmpty();
    }

    @Test
    void deleteChapter_setsPageChapterIdToNull() {
        Chapter chapter = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Глава", 0, null, Instant.now()
        ));
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), chapter.id(), 1,
                "x", null, Instant.now(), Instant.now()
        ));

        chapterRepository.deleteById(chapter.id());

        assertThat(pageRepository.findById(page.id()).orElseThrow().chapterId()).isNull();
    }

    @Test
    void save_pageNumberZero_violatesCheck() {
        Page page = new Page(
                UUID.randomUUID(), book.id(), null, 0,
                "x", null, Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> pageRepository.save(page))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
