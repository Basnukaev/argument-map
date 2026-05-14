package ru.basnukaev.argumentmap.library.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.domain.ImageRegion;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.repository.ImageRegionRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.web.dto.CreateBookRequest;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BookControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ImageRegionRepository imageRegionRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

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
    void createBook_happyPath_returns201_withLocationAndBody() throws Exception {
        JsonNode metadata = objectMapper.readTree("{\"shamela_id\":12345}");
        var req = new CreateBookRequest(
                BookType.BOOK, "Маджму' аль-Фатава", null, "ar",
                "сборник", metadata
        );

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/library/books/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.bookType").value("BOOK"))
                .andExpect(jsonPath("$.title").value("Маджму' аль-Фатава"))
                .andExpect(jsonPath("$.metadata.shamela_id").value(12345))
                .andExpect(jsonPath("$.createdBy").value(userId.toString()));
    }

    @Test
    void createBook_quranWithoutAuthor_returns201() throws Exception {
        var req = new CreateBookRequest(BookType.QURAN, "Коран", null, "ar", null, null);

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookType").value("QURAN"))
                .andExpect(jsonPath("$.authorityId").doesNotExist());
    }

    @Test
    void createBook_invalidAuthorityId_returns404_authorityNotFound() throws Exception {
        var req = new CreateBookRequest(
                BookType.BOOK, "T", UUID.randomUUID(), "ar", null, null
        );

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("authority-not-found")));
    }

    @Test
    void createBook_blankTitle_returns400_validation() throws Exception {
        var req = new CreateBookRequest(BookType.BOOK, "  ", null, "ar", null, null);

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='title')]").exists());
    }

    @Test
    void createBook_nullBookType_returns400() throws Exception {
        String json = "{\"bookType\":null,\"title\":\"T\",\"language\":\"ar\"}";

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBook_withoutUserHeader_returns400() throws Exception {
        var req = new CreateBookRequest(BookType.BOOK, "T", null, "ar", null, null);

        mockMvc.perform(post("/api/v1/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("missing-user-header")));
    }

    @Test
    void listBooks_returnsAllSummaries() throws Exception {
        saveBook("a", BookType.BOOK);
        saveBook("b", BookType.QURAN);

        mockMvc.perform(get("/api/v1/library/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listBooks_withQueryFilter_returnsMatching() throws Exception {
        saveBook("Сахих аль-Бухари", BookType.HADITH_COLLECTION);
        saveBook("Муснад Ахмада", BookType.HADITH_COLLECTION);

        mockMvc.perform(get("/api/v1/library/books").param("q", "сахих"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Сахих аль-Бухари"));
    }

    @Test
    void listBooks_withTypeFilter_returnsMatching() throws Exception {
        saveBook("Коран", BookType.QURAN);
        saveBook("Книга", BookType.BOOK);

        mockMvc.perform(get("/api/v1/library/books").param("type", "QURAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookType").value("QURAN"));
    }

    @Test
    void getBook_existing_returns200_withChaptersTree() throws Exception {
        Book book = saveBook("Книга", BookType.BOOK);
        Chapter root = chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), null, "Том 1", 0, null, Instant.now()
        ));
        chapterRepository.save(new Chapter(
                UUID.randomUUID(), book.id(), root.id(), "Глава 1.1", 0, null, Instant.now()
        ));

        mockMvc.perform(get("/api/v1/library/books/{id}", book.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(book.id().toString()))
                .andExpect(jsonPath("$.chapters.length()").value(1))
                .andExpect(jsonPath("$.chapters[0].title").value("Том 1"))
                .andExpect(jsonPath("$.chapters[0].children.length()").value(1))
                .andExpect(jsonPath("$.chapters[0].children[0].title").value("Глава 1.1"));
    }

    @Test
    void getBook_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/library/books/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    @Test
    void deleteBook_existing_returns204() throws Exception {
        Book book = saveBook("x", BookType.BOOK);

        mockMvc.perform(delete("/api/v1/library/books/{id}", book.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/library/books/{id}", book.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_nonexistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/library/books/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    @Test
    void listPages_returnsRangeWithoutContent() throws Exception {
        Book book = saveBook("x", BookType.BOOK);
        for (int i = 1; i <= 5; i++) {
            pageRepository.save(new Page(
                    UUID.randomUUID(), book.id(), null, i,
                    null, null, null,
                    "p" + i, null, Instant.now(), Instant.now()
            ));
        }

        mockMvc.perform(get("/api/v1/library/books/{id}/pages", book.id())
                        .param("from", "2").param("to", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].pageNumber").value(2))
                .andExpect(jsonPath("$[0].hasText").value(true))
                .andExpect(jsonPath("$[0].hasImage").value(false))
                .andExpect(jsonPath("$[0].textContent").doesNotExist());
    }

    @Test
    void listPages_nonexistentBook_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/library/books/{id}/pages", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    @Test
    void getPage_existing_returns200_withRegions() throws Exception {
        Book book = saveBook("x", BookType.MANUSCRIPT);
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                null, "https://x/scan.jpg", Instant.now(), Instant.now()
        ));
        imageRegionRepository.save(new ImageRegion(
                UUID.randomUUID(), page.id(), 0.1, 0.1, 0.5, 0.5, "بسم الله", Instant.now()
        ));

        mockMvc.perform(get("/api/v1/library/pages/{id}", page.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(page.id().toString()))
                .andExpect(jsonPath("$.imageUrl").value("https://x/scan.jpg"))
                .andExpect(jsonPath("$.imageRegions.length()").value(1))
                .andExpect(jsonPath("$.imageRegions[0].extractedText").value("بسم الله"));
    }

    @Test
    void getPage_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/library/pages/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("page-not-found")));
    }

    private Book saveBook(String title, BookType type) {
        Instant now = Instant.now();
        return bookRepository.save(new Book(
                UUID.randomUUID(), type, title, null, "ar",
                null, null, userId, now, now
        ));
    }

    private Authority saveAuthor(String name) {
        return authorityRepository.save(new Authority(
                UUID.randomUUID(), name, null, null, null, null, Instant.now(),
                null, null
        ));
    }
}
