package ru.basnukaev.argumentmap.qa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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
import ru.basnukaev.argumentmap.exception.AnswerNotFoundException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.ImageRegionNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidCitationException;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.pdf.service.PdfNotAvailableException;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;
import ru.basnukaev.argumentmap.qa.web.dto.AnswerSourceResponse;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.web.dto.CitationRequest;

/**
 * IT для Этапа 19.d - зеркало {@link QuestionCitationServiceIT}. 3-я
 * итерация ADR-033 параллельной иерархии: same suite reused через
 * substitute question_id → answer_id.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AnswerCitationServiceIT {

    @Autowired private AnswerCitationService service;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private PageRepository pageRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID questionId;
    private UUID answerId;
    private UUID bookId;
    private UUID pageId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");

        questionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO questions (id, title, body, status, asked_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'OPEN', ?, now(), now())",
                questionId, "Как правильно...", "Тело вопроса", userId);

        answerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO answers (id, question_id, body, author_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, now(), now())",
                answerId, questionId, "Тело ответа", userId);

        bookId = UUID.randomUUID();
        bookRepository.save(new Book(bookId, BookType.BOOK, "Тафсир Ибн Касира", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null));

        pageId = UUID.randomUUID();
        pageRepository.save(new Page(pageId, bookId, null, 1, "47", "1", null,
                "وأرى أن لا تكون البدعة...", null, Instant.now(), Instant.now()));
    }

    @Test
    void createCitation_textMode_creates_source_and_answer_source_with_computed_location() {
        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 87,
                null, null, null,
                null,
                "وأرى أن لا تكون...", "Ибн Касир признаёт");

        AnswerSourceResponse response = service.createCitation(answerId, req);

        assertThat(response.answerId()).isEqualTo(answerId);
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

        AnswerSourceResponse response = service.createCitation(answerId, req);

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

        AnswerSourceResponse response = service.createCitation(answerId, req);

        assertThat(response.mode()).isEqualTo(CitationMode.REGION);
        assertThat(response.citation().region().id()).isEqualTo(imageRegionId);
    }

    @Test
    void createCitation_ensureOrCreate_reusesSourceFromOtherContext() {
        // Source может уже существовать от question_sources/node_sources - не дублируется
        UUID existingSourceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title, book_id, created_at) "
                        + "VALUES (?, 'BOOK', ?, ?, now())",
                existingSourceId, "preexisting", bookId);

        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, null, null);

        AnswerSourceResponse response = service.createCitation(answerId, req);

        assertThat(response.sourceId()).isEqualTo(existingSourceId);
        Long sourceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sources WHERE book_id = ?", Long.class, bookId);
        assertThat(sourceCount).isEqualTo(1L);
    }

    @Test
    void createCitation_answerNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(missing, req))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void createCitation_bookNotFound_throws404() {
        UUID missingBook = UUID.randomUUID();
        CitationRequest req = new CitationRequest(missingBook,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(answerId, req))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void createCitation_pageNotFound_throws404() {
        UUID missingPage = UUID.randomUUID();
        CitationRequest req = new CitationRequest(bookId,
                missingPage, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(answerId, req))
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

        assertThatThrownBy(() -> service.createCitation(answerId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("не принадлежит");
    }

    @Test
    void createCitation_imageRegionNotFound_throws404() {
        UUID missingRegion = UUID.randomUUID();
        CitationRequest req = new CitationRequest(bookId,
                null, null, null, null, null, null, missingRegion, null, null);

        assertThatThrownBy(() -> service.createCitation(answerId, req))
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

        assertThatThrownBy(() -> service.createCitation(answerId, req))
                .isInstanceOf(PdfNotAvailableException.class);
    }

    @Test
    void createCitation_invalidMode_noPositionalFields_throws400() {
        CitationRequest req = new CitationRequest(bookId,
                null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(answerId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("Ровно один");
    }

    @Test
    void createCitation_invalidRange_endLteStart_throws400() {
        CitationRequest req = new CitationRequest(bookId,
                pageId, 100, 50, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(answerId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("range");
    }

    @Test
    void createCitation_missingBookId_throws400() {
        CitationRequest req = new CitationRequest(null,
                pageId, 0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createCitation(answerId, req))
                .isInstanceOf(InvalidCitationException.class)
                .hasMessageContaining("bookId");
    }

    @Test
    void getAnswerSourcesWithLocation_returnsAllCitations_orderedByCreatedAt() {
        CitationRequest req1 = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, "first", null);
        CitationRequest req2 = new CitationRequest(bookId,
                pageId, 20, 30, null, null, null, null, "second", null);

        service.createCitation(answerId, req1);
        service.createCitation(answerId, req2);

        List<AnswerSourceResponse> list = service.getAnswerSourcesWithLocation(answerId);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(AnswerSourceResponse::quote)
                .containsExactly("first", "second");
        assertThat(list).allSatisfy(r -> {
            assertThat(r.answerId()).isEqualTo(answerId);
            assertThat(r.citation().book().id()).isEqualTo(bookId);
        });
    }

    @Test
    void getAnswerSourcesWithLocation_emptyList_whenNoCitations() {
        List<AnswerSourceResponse> list = service.getAnswerSourcesWithLocation(answerId);
        assertThat(list).isEmpty();
    }

    @Test
    void getAnswerSourcesWithLocation_answerNotFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.getAnswerSourcesWithLocation(missing))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    void detachById_removesSpecificCitation_leavesOthers() {
        CitationRequest req1 = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, "keep", null);
        CitationRequest req2 = new CitationRequest(bookId,
                pageId, 20, 30, null, null, null, null, "remove", null);

        AnswerSourceResponse first = service.createCitation(answerId, req1);
        AnswerSourceResponse second = service.createCitation(answerId, req2);

        service.detachById(second.id());

        List<AnswerSourceResponse> remaining = service.getAnswerSourcesWithLocation(answerId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).id()).isEqualTo(first.id());
    }

    @Test
    void detachById_notFound_throws404() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.detachById(missing))
                .isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void cascadeDelete_removesAnswerSources_whenAnswerDeleted() {
        CitationRequest req = new CitationRequest(bookId,
                pageId, 0, 10, null, null, null, null, null, null);
        service.createCitation(answerId, req);

        Long before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_sources WHERE answer_id = ?", Long.class, answerId);
        assertThat(before).isEqualTo(1L);

        jdbcTemplate.update("DELETE FROM answers WHERE id = ?", answerId);

        Long after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_sources WHERE answer_id = ?", Long.class, answerId);
        assertThat(after).isEqualTo(0L);
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
