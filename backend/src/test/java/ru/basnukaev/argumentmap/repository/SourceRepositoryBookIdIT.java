package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * Тесты на расширение Source.bookId + uq_sources_book_per_type +
 * chk_sources_book_id_only_for_book_type из миграции 22.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SourceRepositoryBookIdIT {

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUpUser() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    @Test
    void save_persistsBookId_forSourceTypeBOOK() {
        UUID bookId = createBook("Тафсир Ибн Касира");
        Source src = new Source(UUID.randomUUID(), SourceType.BOOK,
                "Тафсир Ибн Касира", null, null, null, bookId, null, Instant.now());

        sourceRepository.save(src);

        Optional<Source> found = sourceRepository.findById(src.id());
        assertThat(found).isPresent();
        assertThat(found.get().bookId()).isEqualTo(bookId);
    }

    @Test
    void findByBookId_returnsSource_whenExists() {
        UUID bookId = createBook("test");
        Source src = new Source(UUID.randomUUID(), SourceType.BOOK,
                "test", null, null, null, bookId, null, Instant.now());
        sourceRepository.save(src);

        Optional<Source> found = sourceRepository.findByBookId(bookId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(src.id());
    }

    @Test
    void findByBookId_returnsEmpty_forUnknownBook() {
        assertThat(sourceRepository.findByBookId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_cannotSetBookId_forNonBookSourceType() {
        UUID bookId = createBook("test");
        Source bad = new Source(UUID.randomUUID(), SourceType.URL,
                "url src", "https://example.com", null, null, bookId, null, Instant.now());

        assertThatThrownBy(() -> sourceRepository.save(bad))
                .hasMessageContaining("chk_sources_book_id_only_for_book_type");
    }

    @Test
    void uniqueConstraint_preventsDuplicateSourcePerBook() {
        UUID bookId = createBook("test");
        Source first = new Source(UUID.randomUUID(), SourceType.BOOK,
                "book", null, null, null, bookId, null, Instant.now());
        sourceRepository.save(first);

        Source second = new Source(UUID.randomUUID(), SourceType.BOOK,
                "book duplicate", null, null, null, bookId, null, Instant.now());

        assertThatThrownBy(() -> sourceRepository.save(second))
                .hasMessageContaining("uq_sources_book_per_type");
    }

    @Test
    void upsertByBookId_createsNewSource_whenNoneExists() {
        UUID bookId = createBook("Новая книга");
        Source src = new Source(UUID.randomUUID(), SourceType.BOOK,
                "Новая книга", null, null, null, bookId, null, Instant.now());

        Source result = sourceRepository.upsertByBookId(src);

        assertThat(result.id()).isEqualTo(src.id());
        assertThat(sourceRepository.findByBookId(bookId)).isPresent();
    }

    @Test
    void upsertByBookId_returnsExisting_onSecondCallWithSameBook() {
        UUID bookId = createBook("test");
        Source first = new Source(UUID.randomUUID(), SourceType.BOOK,
                "first", null, null, null, bookId, null, Instant.now());
        sourceRepository.upsertByBookId(first);

        Source secondAttempt = new Source(UUID.randomUUID(), SourceType.BOOK,
                "second attempt", null, null, null, bookId, null, Instant.now());
        Source result = sourceRepository.upsertByBookId(secondAttempt);

        assertThat(result.id()).isEqualTo(first.id());
        assertThat(result.title()).isEqualTo("first");
    }

    @Test
    void upsertByBookId_rejectsNonBookSourceType() {
        UUID bookId = createBook("test");
        Source bad = new Source(UUID.randomUUID(), SourceType.URL,
                "url", null, null, null, bookId, null, Instant.now());

        assertThatThrownBy(() -> sourceRepository.upsertByBookId(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upsertByBookId");
    }

    private UUID createBook(String title) {
        UUID id = UUID.randomUUID();
        Book b = new Book(id, BookType.BOOK, title, null, "ar", null,
                null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null);
        bookRepository.save(b);
        return id;
    }
}
