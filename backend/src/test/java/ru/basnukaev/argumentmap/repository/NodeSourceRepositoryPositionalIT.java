package ru.basnukaev.argumentmap.repository;

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
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

/**
 * Тесты расширения node_sources positional полями (миграция 23, ADR-027).
 * Покрывают TEXT/PDF/REGION/LEGACY modes, CHECK constraint validation
 * и FK ON DELETE RESTRICT integrity.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeSourceRepositoryPositionalIT {

    @Autowired
    private NodeSourceRepository nodeSourceRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID sourceId;
    private UUID bookId;
    private UUID pageId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");

        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, created_at) VALUES (?, ?, ?, now())",
                topicId, "topic", userId);

        nodeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'CLAIM', 'node content', 'STANDING', ?, now(), now())",
                nodeId, topicId, userId);

        bookId = UUID.randomUUID();
        bookRepository.save(new Book(bookId, BookType.BOOK, "Тестовая книга", null, "ar",
                null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null, BookVisibility.PUBLIC));

        sourceId = UUID.randomUUID();
        sourceRepository.save(new Source(sourceId, SourceType.BOOK, "Тестовая книга",
                null, null, null, bookId, null, Instant.now()));

        pageId = UUID.randomUUID();
        pageRepository.save(new Page(pageId, bookId, null, 1, "1", "1", null,
                "Текст страницы для теста", null, null, Instant.now(), Instant.now()));
    }

    @Test
    void textMode_persistsPageIdAndRange() {
        NodeSource ns = NodeSource.textMode(nodeId, sourceId,
                "цитата", "контекст", "Тестовая книга, Т.1 стр.1, строки 0-87",
                pageId, 0, 87, Instant.now());

        nodeSourceRepository.save(ns);

        Optional<NodeSource> found = nodeSourceRepository.findByIds(nodeId, sourceId);
        assertThat(found).isPresent();
        NodeSource reloaded = found.get();
        assertThat(reloaded.pageId()).isEqualTo(pageId);
        assertThat(reloaded.rangeStart()).isEqualTo(0);
        assertThat(reloaded.rangeEnd()).isEqualTo(87);
        assertThat(reloaded.pdfFileId()).isNull();
        assertThat(reloaded.imageRegionId()).isNull();
        assertThat(reloaded.mode()).isEqualTo(CitationMode.TEXT);
    }

    @Test
    void pdfMode_persistsBboxAsJsonb() {
        UUID pdfFileId = createLibraryFile();
        String bboxJson = "{\"x\":0.12,\"y\":0.23,\"width\":0.5,\"height\":0.04}";

        NodeSource ns = NodeSource.pdfMode(nodeId, sourceId,
                "snapshot quote", "context", "Тестовая книга, PDF стр.47, регион",
                pdfFileId, 47, bboxJson, Instant.now());

        nodeSourceRepository.save(ns);

        Optional<NodeSource> found = nodeSourceRepository.findByIds(nodeId, sourceId);
        assertThat(found).isPresent();
        NodeSource reloaded = found.get();
        assertThat(reloaded.pdfFileId()).isEqualTo(pdfFileId);
        assertThat(reloaded.pdfPageNumber()).isEqualTo(47);
        assertThat(reloaded.pdfBbox()).contains("0.12").contains("0.5");
        assertThat(reloaded.pageId()).isNull();
        assertThat(reloaded.mode()).isEqualTo(CitationMode.PDF);
    }

    @Test
    void regionMode_persistsImageRegionId() {
        UUID imageRegionId = createImageRegion(pageId);

        NodeSource ns = NodeSource.regionMode(nodeId, sourceId,
                null, "контекст", "Тестовая книга, скан стр.1",
                imageRegionId, Instant.now());

        nodeSourceRepository.save(ns);

        Optional<NodeSource> found = nodeSourceRepository.findByIds(nodeId, sourceId);
        assertThat(found).isPresent();
        assertThat(found.get().imageRegionId()).isEqualTo(imageRegionId);
        assertThat(found.get().mode()).isEqualTo(CitationMode.REGION);
    }

    @Test
    void legacyMode_allPositionalNull_works() {
        NodeSource ns = NodeSource.legacyMode(nodeId, sourceId,
                "quote", "context", "стр. 42", Instant.now());

        nodeSourceRepository.save(ns);

        Optional<NodeSource> found = nodeSourceRepository.findByIds(nodeId, sourceId);
        assertThat(found).isPresent();
        assertThat(found.get().pageId()).isNull();
        assertThat(found.get().pdfFileId()).isNull();
        assertThat(found.get().imageRegionId()).isNull();
        assertThat(found.get().mode()).isEqualTo(CitationMode.LEGACY);
    }

    @Test
    void checkConstraint_rejectsMixedTextAndPdfModes() {
        UUID pdfFileId = createLibraryFile();
        String bboxJson = "{\"x\":0,\"y\":0,\"width\":0.5,\"height\":0.5}";
        NodeSource bad = new NodeSource(UUID.randomUUID(), nodeId, sourceId, "q", "c", "loc",
                pageId, 0, 50,
                pdfFileId, 1, bboxJson,
                null,
                Instant.now());

        assertThatThrownBy(() -> nodeSourceRepository.save(bad))
                .hasMessageContaining("chk_node_sources_one_mode");
    }

    @Test
    void checkConstraint_rejectsInvalidRangeEndBeforeStart() {
        NodeSource bad = new NodeSource(UUID.randomUUID(), nodeId, sourceId, "q", "c", "loc",
                pageId, 100, 50,
                null, null, null,
                null,
                Instant.now());

        assertThatThrownBy(() -> nodeSourceRepository.save(bad))
                .hasMessageContaining("chk_node_sources_one_mode");
    }

    @Test
    void checkConstraint_rejectsTextModeWithoutRange() {
        NodeSource bad = new NodeSource(UUID.randomUUID(), nodeId, sourceId, "q", "c", "loc",
                pageId, null, null,
                null, null, null,
                null,
                Instant.now());

        assertThatThrownBy(() -> nodeSourceRepository.save(bad))
                .hasMessageContaining("chk_node_sources_one_mode");
    }

    @Test
    void cannotDeletePage_referencedByCitation() {
        NodeSource ns = NodeSource.textMode(nodeId, sourceId,
                "q", "c", "loc", pageId, 0, 10, Instant.now());
        nodeSourceRepository.save(ns);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM lib_pages WHERE id = ?", pageId))
                .hasMessageContaining("foreign key")
                .hasMessageContaining("node_sources");
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

    private UUID createImageRegion(UUID pageId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_image_regions (id, page_id, x, y, width, height, created_at) "
                        + "VALUES (?, ?, 0.1, 0.2, 0.5, 0.05, now())",
                id, pageId);
        return id;
    }
}
