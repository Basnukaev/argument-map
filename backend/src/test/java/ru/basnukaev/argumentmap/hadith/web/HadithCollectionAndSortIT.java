package ru.basnukaev.argumentmap.hadith.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.service.BookCollectionBridgeService;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookRepository;

/**
 * IT: GET /api/v1/hadith/collections (chip-фильтр) + sort param на списке
 * хадисов (под-проект #1.B). DevHadithSeeder сеет hd_* при старте — чистим.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithCollectionAndSortIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private HadithRepository hadithRepository;

    @Autowired
    private MatnRepository matnRepository;

    @Autowired
    private BookRepository bookRepository;

    private UUID collectionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
        jdbcTemplate.update("DELETE FROM hd_collections");
        jdbcTemplate.update("DELETE FROM hd_narrators");

        Instant now = Instant.now();
        collectionId = UUID.randomUUID();
        collectionRepository.save(new Collection(collectionId, "testcoll",
                "اختبار", "Test Coll", null, null, 100, null, now));
        // три хадиса: номера 10/2/1, матны по алфавиту "ا" < "ب" < "ج"
        hadithRepository.save(new Hadith(UUID.randomUUID(), collectionId, 10,
                "جيم", HadithStatus.VARIANT, null, null, now));
        hadithRepository.save(new Hadith(UUID.randomUUID(), collectionId, 2,
                "الف", HadithStatus.VARIANT, null, null, now));
        UUID h1 = UUID.randomUUID();
        hadithRepository.save(new Hadith(h1, collectionId, 1,
                "باء", HadithStatus.VARIANT, null, null, now));
        // первичный matn для preview-карточки (диакритизированный)
        matnRepository.save(new Matn(UUID.randomUUID(), h1, "بِالنِّيَّاتِ", "بالنيات",
                null, null, collectionId, 1, null, null, true, null, null, now));
    }

    @Test
    void list_includes_preview_matn_from_primary_matn() throws Exception {
        // у хадиса №1 есть первичный matn → previewMatn = его text_ar (с огласовками)
        mockMvc.perform(get("/api/v1/hadith/hadiths?sort=number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].primaryNumber").value(1))
                .andExpect(jsonPath("$.items[0].previewMatn").value("بِالنِّيَّاتِ"));
    }

    @Test
    void collections_endpoint_returns_collection_with_real_hadith_count() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("testcoll"))
                .andExpect(jsonPath("$[0].nameEn").value("Test Coll"))
                .andExpect(jsonPath("$[0].totalHadith").value(100))
                .andExpect(jsonPath("$[0].hadithCount").value(3));
    }

    @Test
    void sort_by_number_orders_by_primary_number_asc() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths?sort=number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].primaryNumber", contains(1, 2, 10)));
    }

    @Test
    void sort_alphabetical_orders_by_normalized_matn() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths?sort=alphabetical"))
                .andExpect(status().isOk())
                // ا(الف) < ب(باء) < ج(جيم) по арабскому алфавиту
                .andExpect(jsonPath("$.items[*].normalizedMatn", contains("الف", "باء", "جيم")));
    }

    @Test
    void filter_by_collectionId_returns_only_that_collection() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths?collectionId=" + collectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void collections_endpoint_carries_book_id_when_bridged() throws Exception {
        // под-проект #3: связываем сборник с книгой-представлением
        UUID bookId = createHadithCollectionBook();
        collectionRepository.updateBookId(collectionId, bookId);

        mockMvc.perform(get("/api/v1/hadith/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookId").value(bookId.toString()));
    }

    @Test
    void by_book_reverse_lookup_returns_collection() throws Exception {
        UUID bookId = createHadithCollectionBook();
        collectionRepository.updateBookId(collectionId, bookId);

        mockMvc.perform(get("/api/v1/hadith/collections/by-book/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(collectionId.toString()))
                .andExpect(jsonPath("$.slug").value("testcoll"))
                .andExpect(jsonPath("$.bookId").value(bookId.toString()));
    }

    @Test
    void by_book_returns_404_when_book_is_not_a_collection() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/collections/by-book/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /** Книга-представление сборника (как создаёт мост): HADITH_COLLECTION/PUBLIC/system. */
    private UUID createHadithCollectionBook() {
        Instant now = Instant.now();
        Book book = new Book(
                UUID.randomUUID(), BookType.HADITH_COLLECTION, "Test Coll", null,
                "ar", null, null, BookCollectionBridgeService.SYSTEM_USER_ID,
                now, now, null, null, null, null, null, null, BookVisibility.PUBLIC);
        bookRepository.save(book);
        return book.id();
    }
}
