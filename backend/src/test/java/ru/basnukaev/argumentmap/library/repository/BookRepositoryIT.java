package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BookRepositoryIT {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

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
    void save_insertsBook_withJsonbMetadata() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Book book = new Book(
                UUID.randomUUID(), BookType.BOOK,
                "Маджму' аль-Фатава", null, "ar",
                "37-томный сборник",
                "{\"volumes\":37}", userId, now, now
        );

        bookRepository.save(book);

        Book reloaded = bookRepository.findById(book.id()).orElseThrow();
        assertThat(reloaded.bookType()).isEqualTo(BookType.BOOK);
        assertThat(reloaded.title()).isEqualTo("Маджму' аль-Фатава");
        assertThat(reloaded.language()).isEqualTo("ar");
        assertThat(reloaded.metadata()).contains("\"volumes\"").contains("37");
        assertThat(reloaded.createdAt()).isEqualTo(now);
        assertThat(reloaded.updatedAt()).isEqualTo(now);
    }

    @Test
    void save_quranWithoutAuthority_persistsNullAuthorityId() {
        Book quran = new Book(
                UUID.randomUUID(), BookType.QURAN,
                "Коран", null, "ar",
                null, null, userId,
                Instant.now(), Instant.now()
        );

        bookRepository.save(quran);

        assertThat(bookRepository.findById(quran.id()).orElseThrow().authorityId()).isNull();
    }

    @Test
    void deleteAuthority_setsBookAuthorityIdToNull() {
        Authority author = authorityRepository.save(new Authority(
                UUID.randomUUID(), "Ибн Таймийя",
                null, "VIII в.х.", "hanbali", null, Instant.now()
        ));
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK,
                "Иктида ас-сырат аль-мустакым",
                author.id(), "ar", null, null, userId,
                Instant.now(), Instant.now()
        ));

        authorityRepository.deleteById(author.id());

        assertThat(bookRepository.findById(book.id()).orElseThrow().authorityId()).isNull();
    }

    @Test
    void findAll_filterByQuery_isCaseInsensitive() {
        bookRepository.save(book("Иктида ас-сырат"));
        bookRepository.save(book("Хусн аль-максыд"));

        List<Book> found = bookRepository.findAll("ИКТИДА", null);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).title()).isEqualTo("Иктида ас-сырат");
    }

    @Test
    void findAll_filterByType_returnsOnlyMatching() {
        bookRepository.save(book("Коран", BookType.QURAN));
        bookRepository.save(book("Сахих аль-Бухари", BookType.HADITH_COLLECTION));
        bookRepository.save(book("Маджму'", BookType.BOOK));

        List<Book> hadiths = bookRepository.findAll(null, BookType.HADITH_COLLECTION);

        assertThat(hadiths).hasSize(1);
        assertThat(hadiths.get(0).title()).isEqualTo("Сахих аль-Бухари");
    }

    @Test
    void findAll_combinedFilters_appliesBoth() {
        bookRepository.save(book("Сахих аль-Бухари", BookType.HADITH_COLLECTION));
        bookRepository.save(book("Сахих Муслим", BookType.HADITH_COLLECTION));
        bookRepository.save(book("Сахих Ибн Хузаймы", BookType.BOOK));

        List<Book> found = bookRepository.findAll("Сахих", BookType.HADITH_COLLECTION);

        assertThat(found).extracting(Book::title)
                .containsExactlyInAnyOrder("Сахих аль-Бухари", "Сахих Муслим");
    }

    @Test
    void findAll_orderByCreatedAt() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Book older = new Book(UUID.randomUUID(), BookType.BOOK, "older",
                null, "ar", null, null, userId, base.minusSeconds(60), base.minusSeconds(60));
        Book newer = new Book(UUID.randomUUID(), BookType.BOOK, "newer",
                null, "ar", null, null, userId, base, base);
        bookRepository.save(newer);
        bookRepository.save(older);

        List<Book> all = bookRepository.findAll(null, null);

        assertThat(all).extracting(Book::id).containsExactly(older.id(), newer.id());
    }

    @Test
    void deleteById_returnsTrueAndRemoves() {
        Book book = bookRepository.save(book("X"));

        boolean deleted = bookRepository.deleteById(book.id());

        assertThat(deleted).isTrue();
        assertThat(bookRepository.findById(book.id())).isEmpty();
    }

    @Test
    void deleteById_whenNotExists_returnsFalse() {
        assertThat(bookRepository.deleteById(UUID.randomUUID())).isFalse();
    }

    @Test
    void metadataJsonb_isQueryableWithGinOperators() {
        bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, "T", null, "ar", null,
                "{\"shamela_id\":12345}", userId,
                Instant.now(), Instant.now()
        ));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_books WHERE metadata @> ?::jsonb",
                Integer.class,
                "{\"shamela_id\":12345}"
        );
        assertThat(count).isOne();
    }

    private Book book(String title) {
        return book(title, BookType.BOOK);
    }

    private Book book(String title, BookType type) {
        Instant now = Instant.now();
        return new Book(UUID.randomUUID(), type, title, null, "ar",
                null, null, userId, now, now);
    }
}
