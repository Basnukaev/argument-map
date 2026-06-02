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
import ru.basnukaev.argumentmap.library.domain.AiEditStatus;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
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
                null, null, userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));
    }

    @Test
    void save_textOnlyPage_persistsCorrectly() {
        Page page = new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "بسم الله", null, null, Instant.now(), Instant.now()
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
                null, null, null,
                null, "https://example.com/scan-1.jpg", null, Instant.now(), Instant.now()
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
                null, null, null,
                null, null, null, Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> pageRepository.save(page))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lib_pages_content_present");
    }

    @Test
    void save_duplicatePageNumberInBook_violatesUniqueConstraint() {
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 5,
                null, null, null,
                "first", null, null, Instant.now(), Instant.now()
        ));

        Page duplicate = new Page(
                UUID.randomUUID(), book.id(), null, 5,
                null, null, null,
                "second", null, null, Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> pageRepository.save(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByBookIdRange_returnsPagesInOrder() {
        for (int i = 1; i <= 10; i++) {
            pageRepository.save(new Page(
                    UUID.randomUUID(), book.id(), null, i,
                    null, null, null,
                    "page " + i, null, null, Instant.now(), Instant.now()
            ));
        }

        List<Page> range = pageRepository.findByBookIdRange(book.id(), 3, 6);

        assertThat(range).extracting(Page::pageNumber).containsExactly(3, 4, 5, 6);
    }

    @Test
    void deleteBook_cascadesPages() {
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "x", null, null, Instant.now(), Instant.now()
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
                null, null, null,
                "x", null, null, Instant.now(), Instant.now()
        ));

        chapterRepository.deleteById(chapter.id());

        assertThat(pageRepository.findById(page.id()).orElseThrow().chapterId()).isNull();
    }

    @Test
    void save_pageNumberZero_violatesCheck() {
        Page page = new Page(
                UUID.randomUUID(), book.id(), null, 0,
                null, null, null,
                "x", null, null, Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> pageRepository.save(page))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_withPrintedPageAndPart_persistsSourceFirstFields() {
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 5,
                "47", "المقدمة", 720,
                "x", null, null, Instant.now(), Instant.now()
        ));

        Page reloaded = pageRepository.findById(page.id()).orElseThrow();
        assertThat(reloaded.printedPage()).isEqualTo("47");
        assertThat(reloaded.part()).isEqualTo("المقدمة");
        assertThat(reloaded.pdfPageNumber()).isEqualTo(720);
    }

    @Test
    void findDistinctPartsByBookId_returnsUniqueOrderedParts() {
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                "1", "المقدمة", null, "p1", null, null, Instant.now(), Instant.now()
        ));
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 2,
                "2", "المقدمة", null, "p2", null, null, Instant.now(), Instant.now()
        ));
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 3,
                "1", "1", null, "p3", null, null, Instant.now(), Instant.now()
        ));
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 4,
                "2", "2", null, "p4", null, null, Instant.now(), Instant.now()
        ));
        pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 5,
                null, null, null, "p5", null, null, Instant.now(), Instant.now()
        ));

        List<String> parts = pageRepository.findDistinctPartsByBookId(book.id());

        assertThat(parts).containsExactly("المقدمة", "1", "2");
    }

    private static final int TIMEOUT_MINUTES = 10;

    @Test
    void tryClaimAiEditProcessing_freshNonProcessingPage_claimWins() {
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "text", null, null, Instant.now(), Instant.now()
        ));

        boolean claimed = pageRepository.tryClaimAiEditProcessing(
                page.id(), AiEditStatus.PROCESSING, Instant.now(), TIMEOUT_MINUTES);

        assertThat(claimed).isTrue();
        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.PROCESSING);
        assertThat(after.aiEditStartedAt()).isNotNull();
    }

    @Test
    void tryClaimAiEditProcessing_freshProcessingPage_claimLoses() {
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "text", null, null, Instant.now(), Instant.now()
        ));
        // свежий PROCESSING (started_at = now) - другой worker в полёте
        pageRepository.tryClaimAiEditProcessing(
                page.id(), AiEditStatus.PROCESSING, Instant.now(), TIMEOUT_MINUTES);

        boolean claimed = pageRepository.tryClaimAiEditProcessing(
                page.id(), AiEditStatus.PROCESSING, Instant.now(), TIMEOUT_MINUTES);

        assertThat(claimed).isFalse();
    }

    @Test
    void tryClaimAiEditProcessing_staleProcessingPage_claimWinsAndRefreshesStartedAt() {
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "text", null, null, Instant.now(), Instant.now()
        ));
        // эмулируем «зависший» PROCESSING: worker умер 30 минут назад,
        // started_at протух относительно TIMEOUT_MINUTES=10
        Instant stale = Instant.now().minus(java.time.Duration.ofMinutes(30));
        jdbcTemplate.update(
                "UPDATE lib_pages SET ai_edit_status = ?, ai_edit_started_at = ? WHERE id = ?",
                AiEditStatus.PROCESSING,
                stale.atOffset(java.time.ZoneOffset.UTC),
                page.id());

        Instant freshClaim = Instant.now();
        boolean claimed = pageRepository.tryClaimAiEditProcessing(
                page.id(), AiEditStatus.PROCESSING, freshClaim, TIMEOUT_MINUTES);

        assertThat(claimed).isTrue();
        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.PROCESSING);
        // started_at обновлён на свежее значение (не остался stale)
        assertThat(after.aiEditStartedAt()).isAfter(stale.plus(java.time.Duration.ofMinutes(20)));
    }
}
