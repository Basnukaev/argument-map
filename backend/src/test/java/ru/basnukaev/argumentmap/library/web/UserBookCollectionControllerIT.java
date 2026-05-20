package ru.basnukaev.argumentmap.library.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.web.dto.AddToCollectionRequest;

/**
 * IT для PSC personal book collections (Vision 49d Section 2.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UserBookCollectionControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BookRepository bookRepository;

    private UUID userId;
    private UUID otherUserId;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'USER')",
                userId, "ubc-user-" + userId, userId + "@test.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'USER')",
                otherUserId, "ubc-other-" + otherUserId, otherUserId + "@test.com"
        );
        Book book = new Book(
                UUID.randomUUID(), BookType.BOOK, "Test книга", null,
                "ru", null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null,
                BookVisibility.PUBLIC
        );
        bookRepository.save(book);
        bookId = book.id();
    }

    @Test
    void POST_addToCollection_returns201() throws Exception {
        var req = new AddToCollectionRequest(bookId, null);

        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.collectionName").value("Избранное"));
    }

    @Test
    void POST_idempotent_duplicateReturnsExisting() throws Exception {
        var req = new AddToCollectionRequest(bookId, "Тафсир");
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Повторный POST того же (user, book, collection) → 201 без 409
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionName").value("Тафсир"));
    }

    @Test
    void POST_nonExistentBook_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        var req = new AddToCollectionRequest(ghost, null);

        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void GET_listAll_returnsOnlyMyEntries() throws Exception {
        // Добавим запись от user'а и от other
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(bookId, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(bookId, null))))
                .andExpect(status().isCreated());

        // Listing user'а возвращает только его entry, не other'а
        mockMvc.perform(get("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookId").value(bookId.toString()));
    }

    @Test
    void GET_filterByName_returnsOnlyMatching() throws Exception {
        // user добавляет в две разные коллекции
        UUID secondBookId = UUID.randomUUID();
        Book second = new Book(secondBookId, BookType.BOOK, "Книга 2", null,
                "ru", null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null, BookVisibility.PUBLIC);
        bookRepository.save(second);

        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(bookId, "Тафсир"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(secondBookId, "Хадис"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/library/collections")
                        .param("name", "Тафсир")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].collectionName").value("Тафсир"));
    }

    @Test
    void DELETE_removesEntry_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(bookId, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/library/collections/{bookId}", bookId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void DELETE_idempotent_returns204_evenIfNotExist() throws Exception {
        mockMvc.perform(delete("/api/v1/library/collections/{bookId}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void GET_names_returnsUniqueCollectionNames() throws Exception {
        UUID secondBookId = UUID.randomUUID();
        Book second = new Book(secondBookId, BookType.BOOK, "Книга 2", null,
                "ru", null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null, BookVisibility.PUBLIC);
        bookRepository.save(second);

        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(bookId, "Тафсир"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(secondBookId, "Тафсир"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/library/collections")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToCollectionRequest(bookId, "Хадис"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/library/collections/names")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("Тафсир"))
                .andExpect(jsonPath("$[1]").value("Хадис"));
    }
}
