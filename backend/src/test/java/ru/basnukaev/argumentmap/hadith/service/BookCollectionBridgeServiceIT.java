package ru.basnukaev.argumentmap.hadith.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * IT моста hd_collections ↔ lib_books (под-проект #3). Доказывает: лениво
 * создаётся lib_books HADITH_COLLECTION-строка, проставляется
 * hd_collections.book_id, операция идемпотентна, обратный lookup работает.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookCollectionBridgeServiceIT {

    @Autowired
    private BookCollectionBridgeService bridgeService;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
        jdbcTemplate.update("UPDATE hd_collections SET book_id = NULL");
        jdbcTemplate.update("DELETE FROM hd_collections");
        jdbcTemplate.update("DELETE FROM hd_narrators");
    }

    @Test
    void ensure_creates_public_hadith_collection_book_and_sets_bridge_fk() {
        Collection c = saveCollection("bukhari", "صحيح البخاري", "Sahih al-Bukhari", null);

        UUID bookId = bridgeService.ensureLibraryBookForCollection(c);

        // lib_books-строка создана: тип HADITH_COLLECTION, PUBLIC, владелец — система
        Book book = bookRepository.findById(bookId).orElseThrow();
        assertThat(book.bookType()).isEqualTo(BookType.HADITH_COLLECTION);
        assertThat(book.visibility()).isEqualTo(BookVisibility.PUBLIC);
        assertThat(book.createdBy()).isEqualTo(BookCollectionBridgeService.SYSTEM_USER_ID);
        // title резолвится nameRu → nameAr → slug (здесь nameRu=null → nameAr)
        assertThat(book.title()).isEqualTo("صحيح البخاري");

        // мост проставлен на сборнике
        assertThat(collectionRepository.findById(c.id()).orElseThrow().bookId())
                .isEqualTo(bookId);
    }

    @Test
    void title_prefers_name_ru_then_ar_then_slug() {
        Collection withRu = saveCollection("muslim", "صحيح مسلم", "Sahih Muslim", "Сахих Муслим");
        UUID bookId = bridgeService.ensureLibraryBookForCollection(withRu);
        assertThat(bookRepository.findById(bookId).orElseThrow().title())
                .isEqualTo("Сахих Муслим");
    }

    @Test
    void ensure_is_idempotent_returns_existing_book_without_duplicating() {
        Collection c = saveCollection("bukhari", "صحيح البخاري", null, null);

        UUID first = bridgeService.ensureLibraryBookForCollection(c);
        // повторный вызов с уже-связанным сборником: вернуть тот же book_id, без дубля
        Collection reloaded = collectionRepository.findById(c.id()).orElseThrow();
        UUID second = bridgeService.ensureLibraryBookForCollection(reloaded);

        assertThat(second).isEqualTo(first);
        Integer bookCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lib_books WHERE id = ?", Integer.class, first);
        assertThat(bookCount).isEqualTo(1);
    }

    @Test
    void reverse_lookup_resolves_collection_from_book_id() {
        Collection c = saveCollection("bukhari", "صحيح البخاري", null, null);
        UUID bookId = bridgeService.ensureLibraryBookForCollection(c);

        Collection resolved = collectionRepository.findByBookId(bookId).orElseThrow();
        assertThat(resolved.id()).isEqualTo(c.id());
        assertThat(resolved.slug()).isEqualTo("bukhari");
    }

    private Collection saveCollection(String slug, String nameAr, String nameEn, String nameRu) {
        return collectionRepository.save(new Collection(
                UUID.randomUUID(), slug, nameAr, nameEn, nameRu,
                null, null, null, Instant.now()));
    }
}
