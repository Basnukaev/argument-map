package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.CitationMode;
import ru.basnukaev.argumentmap.domain.PdfBbox;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidCitationException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;
import ru.basnukaev.argumentmap.web.dto.NodeSourceResponse;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeCitationServiceIT {

    @Autowired private NodeCitationService service;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private PageRepository pageRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, created_at) VALUES (?, ?, ?, now())",
                topicId, "topic", userId);

        nodeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'CLAIM', 'content', 'STANDING', ?, now(), now())",
                nodeId, topicId, userId);

        bookId = UUID.randomUUID();
        bookRepository.save(new Book(bookId, BookType.BOOK, "Тафсир Ибн Касира", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null));

        pageId = UUID.randomUUID();
        pageRepository.save(new Page(pageId, bookId, null, 1, "47", "1", null,
                "وأرى أن لا تكون البدعة...", null, Instant.now(), Instant.now()));
    }

    @Test
    void createCitation_textMode_creates_source_and_node_source_with_computed_location() {
        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 87,
                null, null, null,
                null,
                "وأرى أن لا تكون...", "Ибн Касир признаёт");

        NodeSourceResponse response = service.createCitation(nodeId, req);

        assertThat(response.mode()).isEqualTo(CitationMode.TEXT);
        assertThat(response.citation().location().pageId()).isEqualTo(pageId);
        assertThat(response.citation().location().rangeStart()).isEqualTo(0);
        assertThat(response.citation().location().rangeEnd()).isEqualTo(87);
        assertThat(response.citation().book().id()).isEqualTo(bookId);
        assertThat(response.citation().book().title()).isEqualTo("Тафсир Ибн Касира");
        assertThat(response.citation().location().part()).isEqualTo("1");
        assertThat(response.citation().location().printedPage()).isEqualTo("47");

        Optional<Source> src = sourceRepository.findByBookId(bookId);
        assertThat(src).isPresent();
        assertThat(src.get().sourceType()).isEqualTo(SourceType.BOOK);
        assertThat(src.get().title()).isEqualTo("Тафсир Ибн Касира");
    }

    @Test
    void createCitation_pdfMode_persistsBboxAndComputedLocation() {
        UUID pdfFileId = createLibraryFile();
        PdfBbox bbox = new PdfBbox(0.1, 0.2, 0.5, 0.04);

        CitationRequest req = new CitationRequest(bookId,
                null, null, null,
                pdfFileId, 47, bbox,
                null,
                null, "PDF citation");

        NodeSourceResponse response = service.createCitation(nodeId, req);

        assertThat(response.mode()).isEqualTo(CitationMode.PDF);
        assertThat(response.citation().pdf().fileId()).isEqualTo(pdfFileId);
        assertThat(response.citation().pdf().pageNumber()).isEqualTo(47);
        assertThat(response.citation().pdf().bbox()).isNotNull();
    }

    @Test
    void createCitation_regionMode_persistsImageRegionId() {
        UUID imageRegionId = createImageRegion(pageId);

        CitationRequest req = new CitationRequest(bookId,
                null, null, null,
                null, null, null,
                imageRegionId,
                null, "region citation");

        NodeSourceResponse response = service.createCitation(nodeId, req);

        assertThat(response.mode()).isEqualTo(CitationMode.REGION);
        assertThat(response.citation().region().id()).isEqualTo(imageRegionId);
    }

    @Test
    void createCitation_ensureOrCreate_reusesSourceOnSecondCitation() {
        CitationRequest req1 = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, "q1", null);
        CitationRequest req2 = new CitationRequest(bookId,
                pageId, 20, 30, null, null, null, null, "q2", null);

        NodeSourceResponse r1 = service.createCitation(nodeId, req1);
        UUID node2 = createSecondNode();
        NodeSourceResponse r2 = service.createCitation(node2, req2);

        assertThat(r2.sourceId()).isEqualTo(r1.sourceId());
        assertThat(sourceRepository.findByBookId(bookId)).isPresent();
        Long sourceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sources WHERE book_id = ?", Long.class, bookId);
        assertThat(sourceCount).isEqualTo(1L);
    }

    @Test
    void createCitation_nodeNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(missing, req))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void createCitation_bookNotFound_throws404() {
        UUID missingBook = UUID.randomUUID();
        CitationRequest req = new CitationRequest(missingBook,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void createCitation_pageNotFound_throws404() {
        UUID missingPage = UUID.randomUUID();
        CitationRequest req = new CitationRequest(bookId,
                missingPage, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(PageNotFoundException.class);
    }

    @Test
    void createCitation_pageWrongBook_throws400() {
        UUID otherBookId = UUID.randomUUID();
        bookRepository.save(new Book(otherBookId, BookType.BOOK, "other", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null));

        CitationRequest req = new CitationRequest(otherBookId,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("не принадлежит");
    }

    @Test
    void createCitation_imageRegionNotFound_throws404() {
        UUID missingRegion = UUID.randomUUID();
        CitationRequest req = new CitationRequest(bookId,
                null, null, null, null, null, null, missingRegion, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(ImageRegionNotFoundException.class);
    }

    @Test
    void createCitation_pdfNotAvailable_softDeletedFile_throws404() {
        UUID pdfFileId = createLibraryFile();
        jdbcTemplate.update("UPDATE library_files SET deleted_at = now() WHERE file_id = ?", pdfFileId);

        CitationRequest req = new CitationRequest(bookId,
                null, null, null,
                pdfFileId, 1, new PdfBbox(0, 0, 0.5, 0.5),
                null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(PdfNotAvailableException.class);
    }

    @Test
    void createCitation_invalidMode_noPositionalFields_throws400() {
        CitationRequest req = new CitationRequest(bookId,
                null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("Ровно один");
    }

    @Test
    void createCitation_invalidMode_bothTextAndPdf_throws400() {
        UUID pdfFileId = createLibraryFile();
        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 10,
                pdfFileId, 1, new PdfBbox(0, 0, 0.5, 0.5),
                null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("Ровно один");
    }

    @Test
    void createCitation_invalidRange_endLteStart_throws400() {
        CitationRequest req = new CitationRequest(bookId,
                pageId, 100, 50, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("range");
    }

    @Test
    void createCitation_invalidPdfPage_zero_throws400() {
        UUID pdfFileId = createLibraryFile();
        CitationRequest req = new CitationRequest(bookId,
                null, null, null,
                pdfFileId, 0, new PdfBbox(0, 0, 0.5, 0.5),
                null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(InvalidCitationException.class);
    }

    @Test
    void createCitation_missingBookId_throws400() {
        CitationRequest req = new CitationRequest(null,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(nodeId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("bookId");
    }

    private UUID createSecondNode() {
        UUID nid = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'CLAIM', 'second', 'STANDING', ?, now(), now())",
                nid, topicId, userId);
        return nid;
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

    private UUID createImageRegion(UUID pgId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_image_regions (id, page_id, x, y, width, height, created_at) "
                        + "VALUES (?, ?, 0.1, 0.2, 0.5, 0.05, now())",
                id, pgId);
        return id;
    }
}
