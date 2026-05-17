package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
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
import ru.basnukaev.argumentmap.domain.PdfBbox;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeCitationControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BookRepository bookRepository;
    @Autowired private PageRepository pageRepository;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID bookId;
    private UUID pageId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");
        topicId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO topics (id, title, created_by, created_at) VALUES (?, ?, ?, now())",
                topicId, "T", userId);
        nodeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'CLAIM', 'c', 'STANDING', ?, now(), now())",
                nodeId, topicId, userId);
        bookId = UUID.randomUUID();
        bookRepository.save(new Book(bookId, BookType.BOOK, "Тестовая книга", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null, BookVisibility.PUBLIC));
        pageId = UUID.randomUUID();
        pageRepository.save(new Page(pageId, bookId, null, 1, "1", "1", null,
                "текст", null, null, Instant.now(), Instant.now()));
    }

    @Test
    void post_textMode_returns201() throws Exception {
        var req = new CitationRequest(bookId,
                pageId, 0, 87, null, null, null, null,
                "quote", "context");

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/citations", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("TEXT"))
                .andExpect(jsonPath("$.citation.location.pageId").value(pageId.toString()))
                .andExpect(jsonPath("$.citation.location.rangeStart").value(0))
                .andExpect(jsonPath("$.citation.location.rangeEnd").value(87))
                .andExpect(jsonPath("$.citation.book.id").value(bookId.toString()))
                .andExpect(jsonPath("$.citation.book.title").value(containsString("Тестовая книга")));
    }

    @Test
    void post_pdfMode_returns201() throws Exception {
        UUID pdfFileId = createLibraryFile();
        var req = new CitationRequest(bookId,
                null, null, null,
                pdfFileId, 47, new PdfBbox(0.1, 0.1, 0.5, 0.05),
                null, null, "ctx");

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/citations", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("PDF"))
                .andExpect(jsonPath("$.citation.pdf.fileId").value(pdfFileId.toString()))
                .andExpect(jsonPath("$.citation.pdf.pageNumber").value(47))
                .andExpect(jsonPath("$.citation.pdf.bbox.x").value(0.1));
    }

    @Test
    void post_invalidMode_noPositional_returns400() throws Exception {
        var req = new CitationRequest(bookId,
                null, null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/citations", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-citation")));
    }

    @Test
    void post_nodeNotFound_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        var req = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/citations", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void post_bookNotFound_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        var req = new CitationRequest(missing,
                pageId, 0, 10, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/citations", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("book-not-found")));
    }

    @Test
    void post_invalidBbox_returns400() throws Exception {
        // bbox с координатами вне 0-1 - PdfBbox конструктор бросит при десериализации
        String body = "{\"bookId\":\"" + bookId + "\","
                + "\"pdfFileId\":\"" + UUID.randomUUID() + "\","
                + "\"pdfPageNumber\":1,"
                + "\"pdfBbox\":{\"x\":2.0,\"y\":0.1,\"width\":0.5,\"height\":0.5}}";

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/citations", nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private UUID createLibraryFile() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO library_files (file_id, bucket, storage_key, source_type, "
                        + "size_bytes, content_hash, book_id, downloaded_at) "
                        + "VALUES (?, 'library-imported-books', ?, 'SHAMELA', "
                        + "12345, 'abc123', ?, now())",
                id, "test-" + id + ".pdf", bookId);
        return id;
    }
}
