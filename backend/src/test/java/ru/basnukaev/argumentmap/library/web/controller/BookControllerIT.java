package ru.basnukaev.argumentmap.library.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
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
                "сборник", metadata,
                null, null, null, null, null, null
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
        var req = new CreateBookRequest(BookType.QURAN, "Коран", null, "ar", null, null,
                null, null, null, null, null, null);

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
                BookType.BOOK, "T", UUID.randomUUID(), "ar", null, null,
                null, null, null, null, null, null
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
        var req = new CreateBookRequest(BookType.BOOK, "  ", null, "ar", null, null,
                null, null, null, null, null, null);

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
    void createBook_withoutUserHeader_returns401() throws Exception {
        // ADR-040 + b9da308: permitAll в dev/test, но @CurrentUser резолвер
        // при anonymous principal бросает InvalidTokenException → 401
        // invalid-token (frontend refresh-on-401 interceptor trigger)
        var req = new CreateBookRequest(BookType.BOOK, "T", null, "ar", null, null,
                null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
    }

    @Test
    void listBooks_returnsPagedResponseSummaries() throws Exception {
        saveBook("a", BookType.BOOK);
        saveBook("b", BookType.QURAN);

        mockMvc.perform(get("/api/v1/library/books")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getBooks_returnsCreatedBy() throws Exception {
        // backlog tech debt round 4 #8 - createdBy в summary для точного
        // фильтра «Мои» на фронте (без approximation visibility=PRIVATE)
        saveBook("Книга пользователя", BookType.BOOK);

        mockMvc.perform(get("/api/v1/library/books")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].createdBy").value(userId.toString()));
    }

    @Test
    void listBooks_withQueryFilter_returnsMatching() throws Exception {
        saveBook("Сахих аль-Бухари", BookType.HADITH_COLLECTION);
        saveBook("Муснад Ахмада", BookType.HADITH_COLLECTION);

        mockMvc.perform(get("/api/v1/library/books").param("q", "сахих")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Сахих аль-Бухари"));
    }

    @Test
    void listBooks_withTypeFilter_returnsMatching() throws Exception {
        saveBook("Коран", BookType.QURAN);
        saveBook("Книга", BookType.BOOK);

        mockMvc.perform(get("/api/v1/library/books").param("type", "QURAN")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].bookType").value("QURAN"));
    }

    @Test
    void listBooks_paginated_returnsCorrectPage() throws Exception {
        for (int i = 0; i < 5; i++) {
            saveBook("book-" + i, BookType.BOOK);
        }
        mockMvc.perform(get("/api/v1/library/books")
                        .param("page", "1").param("size", "2")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrev").value(true));
    }

    @Test
    void listBooks_filterByAuthorityId_returnsOnlyMatching() throws Exception {
        UUID authorityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO authorities (id, name, created_at) VALUES (?, ?, now())",
                authorityId, "Аль-Бухари"
        );
        saveBookWithAuthority("Сахих", BookType.HADITH_COLLECTION, authorityId);
        saveBookWithAuthority("Other", BookType.BOOK, null);

        mockMvc.perform(get("/api/v1/library/books")
                        .param("authorityId", authorityId.toString())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Сахих"));
    }

    private Book saveBookWithAuthority(String title, BookType type, UUID authorityId) {
        Instant now = Instant.now();
        return bookRepository.save(new Book(
                UUID.randomUUID(), type, title, authorityId, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));
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

        mockMvc.perform(get("/api/v1/library/books/{id}", book.id())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(book.id().toString()))
                .andExpect(jsonPath("$.chapters.length()").value(1))
                .andExpect(jsonPath("$.chapters[0].title").value("Том 1"))
                .andExpect(jsonPath("$.chapters[0].children.length()").value(1))
                .andExpect(jsonPath("$.chapters[0].children[0].title").value("Глава 1.1"));
    }

    @Test
    void getBook_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/library/books/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    @Test
    void deleteBook_existing_returns204() throws Exception {
        Book book = saveBook("x", BookType.BOOK);

        // ADR-043 Amendment: owner может delete
        mockMvc.perform(delete("/api/v1/library/books/{id}", book.id())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/library/books/{id}", book.id())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_nonexistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/library/books/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    @Test
    void deleteBook_nonOwner_returns403() throws Exception {
        // ADR-043 Amendment (Этап 22.c): only owner or admin
        Book book = saveBook("x", BookType.BOOK);
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "user-" + otherUserId, otherUserId + "@example.com"
        );

        mockMvc.perform(delete("/api/v1/library/books/{id}", book.id())
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-book-write")));
    }

    @Test
    void getBook_PRIVATE_nonOwner_returns403() throws Exception {
        // ADR-043 Amendment: PRIVATE book - не owner не видит
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "user-" + otherUserId, otherUserId + "@example.com"
        );
        UUID privateBookId = UUID.randomUUID();
        Instant now = Instant.now();
        bookRepository.save(new Book(
                privateBookId, BookType.BOOK, "Private", null, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null,
                BookVisibility.PRIVATE
        ));

        mockMvc.perform(get("/api/v1/library/books/{id}", privateBookId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-book-access")));
    }

    @Test
    void listBooks_PUBLIC_visibleToAll() throws Exception {
        // ADR-043 Amendment: PUBLIC book виден другому user'у
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "user-" + otherUserId, otherUserId + "@example.com"
        );
        saveBook("Public book", BookType.BOOK);

        mockMvc.perform(get("/api/v1/library/books")
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listBooks_PRIVATE_invisibleToOther() throws Exception {
        // ADR-043 Amendment: PRIVATE book НЕ виден другому user'у
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "user-" + otherUserId, otherUserId + "@example.com"
        );
        UUID privateBookId = UUID.randomUUID();
        Instant now = Instant.now();
        bookRepository.save(new Book(
                privateBookId, BookType.BOOK, "Private", null, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null,
                BookVisibility.PRIVATE
        ));

        mockMvc.perform(get("/api/v1/library/books")
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listPages_returnsRangeWithoutContent() throws Exception {
        Book book = saveBook("x", BookType.BOOK);
        for (int i = 1; i <= 5; i++) {
            pageRepository.save(new Page(
                    UUID.randomUUID(), book.id(), null, i,
                    null, null, null,
                    "p" + i, null, null, Instant.now(), Instant.now()
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
                null, "https://x/scan.jpg", null, Instant.now(), Instant.now()
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

    @Test
    void updateFormattedContent_validProseMirrorJson_returns200_andPersists() throws Exception {
        // Этап 17.0 - Tiptap editor save flow. Frontend Tiptap.getJSON()
        // даёт ProseMirror JSON, кладём в lib_pages.formatted_content jsonb
        Book book = saveBook("x", BookType.BOOK);
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "raw text", null, null, Instant.now(), Instant.now()
        ));

        String body = """
                {
                  "formattedContent": {
                    "type": "doc",
                    "content": [
                      {
                        "type": "hadithBox",
                        "attrs": {"source": "Бухари 1", "grade": "sahih"},
                        "content": [
                          {"type": "paragraph", "content": [
                            {"type": "text", "text": "إنما الأعمال بالنيات"}
                          ]}
                        ]
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(patch("/api/v1/library/pages/{id}/formatted-content", page.id())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(page.id().toString()))
                .andExpect(jsonPath("$.formattedContent.type").value("doc"))
                .andExpect(jsonPath("$.formattedContent.content[0].type").value("hadithBox"))
                .andExpect(jsonPath("$.formattedContent.content[0].attrs.source").value("Бухари 1"))
                .andExpect(jsonPath("$.formattedContent.content[0].attrs.grade").value("sahih"))
                // text_content не трогаем - сохраняется для FTS / fallback
                .andExpect(jsonPath("$.textContent").value("raw text"));

        // GET returns ту же formattedContent после save (GET permit-all в dev profile)
        mockMvc.perform(get("/api/v1/library/pages/{id}", page.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formattedContent.content[0].type").value("hadithBox"));
    }

    @Test
    void updateFormattedContent_invalidJson_returns400() throws Exception {
        Book book = saveBook("x", BookType.BOOK);
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "x", null, null, Instant.now(), Instant.now()
        ));

        // Невалидный JSON - Spring Jackson отклонит на этапе body deserialization
        mockMvc.perform(patch("/api/v1/library/pages/{id}/formatted-content", page.id())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formattedContent\": \"not-valid-json-just-string-but-let's-see"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFormattedContent_emptyDoc_returns200_andClearsContent() throws Exception {
        // Empty doc - валидный ProseMirror контейнер без content. Tiptap
        // даёт такое если user удалил всё. Backend принимает - не баг
        Book book = saveBook("x", BookType.BOOK);
        Page page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "x", null, null, Instant.now(), Instant.now()
        ));

        mockMvc.perform(patch("/api/v1/library/pages/{id}/formatted-content", page.id())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formattedContent\": {\"type\":\"doc\",\"content\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formattedContent.type").value("doc"))
                .andExpect(jsonPath("$.formattedContent.content.length()").value(0));
    }

    @Test
    void updateFormattedContent_nonexistentPage_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/library/pages/{id}/formatted-content", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formattedContent\": {\"type\":\"doc\",\"content\":[]}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("page-not-found")));
    }

    @Test
    void createBook_withAcademicFields_returns201_andPersistsFKs() throws Exception {
        var req = new CreateBookRequest(
                BookType.BOOK, "Иктида", null, "ar", null, null,
                "Аль-Альбани", "Дар Тайба", "Эр-Рияд",
                3, 1432, 2011
        );

        String createJson = mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID bookId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // BookDetailResponse через GET содержит academic FK + nested refs
        mockMvc.perform(get("/api/v1/library/books/{id}", bookId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muhaqqiqId").exists())
                .andExpect(jsonPath("$.publisherId").exists())
                .andExpect(jsonPath("$.publicationPlaceId").exists())
                .andExpect(jsonPath("$.editionNumber").value(3))
                .andExpect(jsonPath("$.publishedYearHijri").value(1432))
                .andExpect(jsonPath("$.publishedYearGregorian").value(2011))
                .andExpect(jsonPath("$.muhaqqiq.name").value("Аль-Альбани"))
                .andExpect(jsonPath("$.publisher.name").value("Дар Тайба"))
                .andExpect(jsonPath("$.publicationPlace.name").value("Эр-Рияд"));
    }

    @Test
    void createBook_withInvalidEditionNumber_returns400() throws Exception {
        var req = new CreateBookRequest(
                BookType.BOOK, "T", null, "ar", null, null,
                null, null, null, 100, null, null
        );

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='editionNumber')]").exists());
    }

    @Test
    void createBook_withYearOutOfRange_returns400() throws Exception {
        var req = new CreateBookRequest(
                BookType.BOOK, "T", null, "ar", null, null,
                null, null, null, null, 99999, null
        );

        mockMvc.perform(post("/api/v1/library/books")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='publishedYearHijri')]").exists());
    }

    private Book saveBook(String title, BookType type) {
        Instant now = Instant.now();
        return bookRepository.save(new Book(
                UUID.randomUUID(), type, title, null, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));
    }

    private Authority saveAuthor(String name) {
        return authorityRepository.save(new Authority(
                UUID.randomUUID(), name, null, null, null, null, Instant.now(),
                null, null, null
        ));
    }
}
