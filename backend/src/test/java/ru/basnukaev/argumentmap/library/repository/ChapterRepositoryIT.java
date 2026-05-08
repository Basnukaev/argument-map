package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Chapter;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ChapterRepositoryIT {

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
    void save_insertsChapter_findByIdReturnsSame() {
        Chapter chapter = new Chapter(
                UUID.randomUUID(), book.id(), null,
                "Том 1", 0, Instant.now()
        );

        chapterRepository.save(chapter);

        Chapter reloaded = chapterRepository.findById(chapter.id()).orElseThrow();
        assertThat(reloaded.title()).isEqualTo("Том 1");
        assertThat(reloaded.parentChapterId()).isNull();
        assertThat(reloaded.orderIndex()).isZero();
    }

    @Test
    void save_chapterWithParent_persistsHierarchy() {
        Chapter root = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Том 1", 0, Instant.now()
        ));
        Chapter child = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), root.id(), "Книга об омовении", 0, Instant.now()
        ));

        Chapter reloaded = chapterRepository.findById(child.id()).orElseThrow();

        assertThat(reloaded.parentChapterId()).isEqualTo(root.id());
    }

    @Test
    void findByBookId_returnsAllChaptersOfBook() {
        chapterRepository.save(new Chapter(UUID.randomUUID(), book.id(), null, "A", 0, Instant.now()));
        chapterRepository.save(new Chapter(UUID.randomUUID(), book.id(), null, "B", 1, Instant.now()));

        Book otherBook = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "Other", null, "ar",
                null, null, userId, Instant.now(), Instant.now()
        ));
        chapterRepository.save(new Chapter(UUID.randomUUID(), otherBook.id(), null, "X", 0, Instant.now()));

        List<Chapter> result = chapterRepository.findByBookId(book.id());

        assertThat(result).extracting(Chapter::title).containsExactly("A", "B");
    }

    @Test
    void deleteBook_cascadesChapters() {
        Chapter chapter = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "T", 0, Instant.now()
        ));

        bookRepository.deleteById(book.id());

        assertThat(chapterRepository.findById(chapter.id())).isEmpty();
    }

    @Test
    void deleteParentChapter_cascadesChildren() {
        Chapter root = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Root", 0, Instant.now()
        ));
        Chapter child = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), root.id(), "Child", 0, Instant.now()
        ));

        chapterRepository.deleteById(root.id());

        assertThat(chapterRepository.findById(child.id())).isEmpty();
    }

    @Test
    void deleteById_returnsTrue_whenExists() {
        Chapter chapter = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "T", 0, Instant.now()
        ));

        assertThat(chapterRepository.deleteById(chapter.id())).isTrue();
        assertThat(chapterRepository.deleteById(UUID.randomUUID())).isFalse();
    }
}
