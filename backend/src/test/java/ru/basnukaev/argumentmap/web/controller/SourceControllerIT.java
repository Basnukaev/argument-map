package ru.basnukaev.argumentmap.web.controller;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.web.dto.CreateSourceRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SourceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void createSource_hadithWithReliabilityAndMetadata_returns201() throws Exception {
        var metadata = objectMapper.readTree("{\"collection\":\"bukhari\",\"book\":1,\"hadith\":4}");
        var req = new CreateSourceRequest(
                SourceType.HADITH, "Сахих аль-Бухари", "том 1, хадис 4",
                Reliability.SAHIH, null, null, metadata
        );

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/sources/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sourceType").value("HADITH"))
                .andExpect(jsonPath("$.reliability").value("SAHIH"))
                .andExpect(jsonPath("$.metadata.collection").value("bukhari"))
                .andExpect(jsonPath("$.metadata.book").value(1));
    }

    @Test
    void createSource_bookWithoutReliability_returns201() throws Exception {
        var req = new CreateSourceRequest(SourceType.BOOK, "Ихьйа улюм ад-дин", null, null, null, null, null);

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reliability").doesNotExist())
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }

    @Test
    void createSource_reliabilityForNonHadith_returns422() throws Exception {
        var req = new CreateSourceRequest(SourceType.BOOK, "title", null, Reliability.SAHIH, null, null, null);

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(containsString("invalid-source")))
                .andExpect(jsonPath("$.detail").value(containsString("HADITH")));
    }

    @Test
    void createSource_blankTitle_returns400() throws Exception {
        var req = new CreateSourceRequest(SourceType.URL, "  ", null, null, null, null, null);

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='title')]").exists());
    }

    @Test
    void getSource_existing_returns200() throws Exception {
        UUID id = createSource("Источник 1");

        mockMvc.perform(get("/api/v1/sources/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Источник 1"));
    }

    @Test
    void getSource_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/sources/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("source-not-found")));
    }

    @Test
    void listSources_returnsAll() throws Exception {
        createSource("a");
        createSource("b");

        mockMvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listSources_withQuery_filtersByTitle() throws Exception {
        createSource("Сахих аль-Бухари");
        createSource("Муснад Ахмада");

        mockMvc.perform(get("/api/v1/sources").param("q", "сахих"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Сахих аль-Бухари"));
    }

    @Test
    void deleteSource_existing_returns204() throws Exception {
        UUID id = createSource("x");

        mockMvc.perform(delete("/api/v1/sources/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sources/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSource_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/sources/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSource_withBookId_persistsLink_andReturnsBookIdInResponse() throws Exception {
        Book book = saveBook("Иктида ас-сырат");
        var req = new CreateSourceRequest(
                SourceType.BOOK, "Иктида ас-сырат", "цитата",
                null, null, book.id(), null
        );

        String createJson = mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(book.id().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID sourceId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // GET тоже возвращает bookId
        mockMvc.perform(get("/api/v1/sources/{id}", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(book.id().toString()));
    }

    @Test
    void createSource_withNonexistentBookId_returns404_bookNotFound() throws Exception {
        var req = new CreateSourceRequest(
                SourceType.BOOK, "T", null, null, null, UUID.randomUUID(), null
        );

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    private Book saveBook(String title) {
        Instant now = Instant.now();
        return bookRepository.save(new Book(
                UUID.randomUUID(), BookType.BOOK, title, null, "ar",
                null, null, userId, now, now,
                null, null, null, null, null, null
        ));
    }

    private UUID createSource(String title) throws Exception {
        var req = new CreateSourceRequest(SourceType.BOOK, title, null, null, null, null, null);
        String json = mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
