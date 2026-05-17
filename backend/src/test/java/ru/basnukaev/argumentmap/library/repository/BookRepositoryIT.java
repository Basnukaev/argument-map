package ru.basnukaev.argumentmap.library.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
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
                "{\"volumes\":37}", userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC);

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
                Instant.now(), Instant.now(),
                null, null, null, null, null, null
        , BookVisibility.PUBLIC);

        bookRepository.save(quran);

        assertThat(bookRepository.findById(quran.id()).orElseThrow().authorityId()).isNull();
    }

    @Test
    void deleteAuthority_setsBookAuthorityIdToNull() {
        Authority author = authorityRepository.save(new Authority(
                UUID.randomUUID(), "Ибн Таймийя",
                null, "VIII в.х.", "hanbali", null, Instant.now(),
                null, null
        ));
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK,
                "Иктида ас-сырат аль-мустакым",
                author.id(), "ar", null, null, userId,
                Instant.now(), Instant.now(),
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));

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
                null, "ar", null, null, userId, base.minusSeconds(60), base.minusSeconds(60),
                null, null, null, null, null, null, BookVisibility.PUBLIC);
        Book newer = new Book(UUID.randomUUID(), BookType.BOOK, "newer",
                null, "ar", null, null, userId, base, base,
                null, null, null, null, null, null, BookVisibility.PUBLIC);
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
                Instant.now(), Instant.now(),
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));

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
                null, null, userId, now, now,
                null, null, null, null, null, null, BookVisibility.PUBLIC);
    }

    // ADR-028 academic citation metadata

    @Test
    void save_withFullAcademicData_roundTrip() {
        UUID muhaqqiqId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_muhaqqiqs (id, name) VALUES (?, ?)",
                muhaqqiqId, "السلامة"
        );
        UUID publisherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
                publisherId, "Дар Тайба"
        );
        UUID placeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_publication_places (id, name) VALUES (?, ?)",
                placeId, "Эр-Рияд"
        );

        Book book = new Book(
                UUID.randomUUID(), BookType.BOOK,
                "تفسير القرآن العظيم", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                muhaqqiqId, publisherId, placeId,
                2, 1420, 1999
        , BookVisibility.PUBLIC);

        bookRepository.save(book);

        Book reloaded = bookRepository.findById(book.id()).orElseThrow();
        assertThat(reloaded.muhaqqiqId()).isEqualTo(muhaqqiqId);
        assertThat(reloaded.publisherId()).isEqualTo(publisherId);
        assertThat(reloaded.publicationPlaceId()).isEqualTo(placeId);
        assertThat(reloaded.editionNumber()).isEqualTo(2);
        assertThat(reloaded.publishedYearHijri()).isEqualTo(1420);
        assertThat(reloaded.publishedYearGregorian()).isEqualTo(1999);
    }

    @Test
    void save_withPartialAcademicData_persistsNullsForMissing() {
        UUID publisherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
                publisherId, "Дар аль-Фикр"
        );

        Book book = new Book(
                UUID.randomUUID(), BookType.BOOK,
                "Книга с partial data", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                null, publisherId, null,
                null, 1430, null
        , BookVisibility.PUBLIC);

        bookRepository.save(book);

        Book reloaded = bookRepository.findById(book.id()).orElseThrow();
        assertThat(reloaded.muhaqqiqId()).isNull();
        assertThat(reloaded.publisherId()).isEqualTo(publisherId);
        assertThat(reloaded.editionNumber()).isNull();
        assertThat(reloaded.publishedYearHijri()).isEqualTo(1430);
        assertThat(reloaded.publishedYearGregorian()).isNull();
    }

    @Test
    void save_editionNumberZero_violatesCheck() {
        Book bad = new Book(
                UUID.randomUUID(), BookType.BOOK, "bad edition",
                null, "ar", null, null, userId, Instant.now(), Instant.now(),
                null, null, null, 0, null, null
        , BookVisibility.PUBLIC);

        assertThatThrownBy(() -> bookRepository.save(bad))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void save_publishedYearGregorianTooLarge_violatesCheck() {
        Book bad = new Book(
                UUID.randomUUID(), BookType.BOOK, "future book",
                null, "ar", null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, 2500
        , BookVisibility.PUBLIC);

        assertThatThrownBy(() -> bookRepository.save(bad))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
