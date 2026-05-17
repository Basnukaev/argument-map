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

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.library.web.dto.CreateImageRegionRequest;

/**
 * IT для {@code POST/GET/DELETE /api/v1/library/pages/{id}/regions}
 * (Этап 17.c, ADR-041).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ImageRegionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PageRepository pageRepository;

    private UUID userId;
    private Page page;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");

        Instant now = Instant.now();
        Book book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.MANUSCRIPT, "Manuscript",
                null, "ar", null, null, userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));
        page = pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                "placeholder", null, null, now, now
        ));
    }

    @Test
    void POST_createRegion_returns201WithLocation() throws Exception {
        var req = new CreateImageRegionRequest(0.1, 0.2, 0.3, 0.4, "хадис текст");

        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        containsString("/api/v1/library/pages/regions/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.x").value(0.1))
                .andExpect(jsonPath("$.y").value(0.2))
                .andExpect(jsonPath("$.width").value(0.3))
                .andExpect(jsonPath("$.height").value(0.4))
                .andExpect(jsonPath("$.extractedText").value("хадис текст"));
    }

    @Test
    void POST_unknownPageId_returns404() throws Exception {
        var req = new CreateImageRegionRequest(0.0, 0.0, 0.5, 0.5, null);
        UUID unknownPage = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/library/pages/" + unknownPage + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/page-not-found"));
    }

    @Test
    void POST_outOfBoundsCoordinates_returns422DueToDbCheckConstraint() throws Exception {
        // x+width = 0.6+0.5 = 1.1 > 1 - violates DB CHECK lib_image_regions_bounds
        var req = new CreateImageRegionRequest(0.6, 0.0, 0.5, 0.5, null);

        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void POST_zeroWidth_returns400FromBeanValidation() throws Exception {
        var req = new CreateImageRegionRequest(0.0, 0.0, 0.0, 0.5, null);

        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_listByPage_returnsRegionsSortedByCreatedAt() throws Exception {
        // create два региона через POST
        var first = new CreateImageRegionRequest(0.1, 0.1, 0.2, 0.2, "first");
        var second = new CreateImageRegionRequest(0.5, 0.5, 0.1, 0.1, "second");

        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/library/pages/" + page.id() + "/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].extractedText").value("first"))
                .andExpect(jsonPath("$[1].extractedText").value("second"));
    }

    @Test
    void GET_listByPage_emptyForNoRegions() throws Exception {
        mockMvc.perform(get("/api/v1/library/pages/" + page.id() + "/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void DELETE_existingRegion_returns204AndRemovesIt() throws Exception {
        var req = new CreateImageRegionRequest(0.0, 0.0, 0.5, 0.5, "to delete");
        String body = mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/regions")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();

        String regionId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(delete("/api/v1/library/pages/regions/" + regionId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/library/pages/" + page.id() + "/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void DELETE_unknownRegionId_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/library/pages/regions/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/image-region-not-found"));
    }
}
