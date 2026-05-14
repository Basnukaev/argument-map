package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.SourceType;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeSourceRepositoryIT {

    @Autowired
    private NodeSourceRepository nodeSourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        userId = insertUser();
        topicId = insertTopic(userId);
        nodeId = insertNode(topicId, userId);
        sourceId = insertSource();
    }

    @Test
    void save_insertsLink_andFindByIdsReturnsIt() {
        NodeSource link = NodeSource.legacyMode(
                nodeId, sourceId, "точная цитата", "контекст использования",
                "стр. 42", Instant.now()
        );

        nodeSourceRepository.save(link);

        var found = nodeSourceRepository.findByIds(nodeId, sourceId);
        assertThat(found).isPresent();
        assertThat(found.get().quote()).isEqualTo("точная цитата");
        assertThat(found.get().context()).isEqualTo("контекст использования");
        assertThat(found.get().location()).isEqualTo("стр. 42");
    }

    @Test
    void save_withNullLocation_persists() {
        NodeSource link = NodeSource.legacyMode(nodeId, sourceId, "q", "c", null, Instant.now());

        nodeSourceRepository.save(link);

        var reloaded = nodeSourceRepository.findByIds(nodeId, sourceId).orElseThrow();
        assertThat(reloaded.location()).isNull();
    }

    @Test
    void findByNodeId_returnsAllLinksForNode() {
        UUID source2 = insertSource();
        nodeSourceRepository.save(NodeSource.legacyMode(nodeId, sourceId, "a", null, null, Instant.now()));
        nodeSourceRepository.save(NodeSource.legacyMode(nodeId, source2, "b", null, null, Instant.now()));

        List<NodeSource> links = nodeSourceRepository.findByNodeId(nodeId);

        assertThat(links).hasSize(2);
    }

    @Test
    void findBySourceId_returnsAllNodesUsingSource() {
        UUID node2 = insertNode(topicId, userId);
        nodeSourceRepository.save(NodeSource.legacyMode(nodeId, sourceId, "a", null, null, Instant.now()));
        nodeSourceRepository.save(NodeSource.legacyMode(node2, sourceId, "b", null, null, Instant.now()));

        List<NodeSource> links = nodeSourceRepository.findBySourceId(sourceId);

        assertThat(links).extracting(NodeSource::nodeId)
                .containsExactlyInAnyOrder(nodeId, node2);
    }

    @Test
    void delete_removesLink() {
        nodeSourceRepository.save(NodeSource.legacyMode(nodeId, sourceId, null, null, null, Instant.now()));

        boolean deleted = nodeSourceRepository.delete(nodeId, sourceId);

        assertThat(deleted).isTrue();
        assertThat(nodeSourceRepository.findByIds(nodeId, sourceId)).isEmpty();
    }

    @Test
    void nodeDeletion_cascadesLinks() {
        nodeSourceRepository.save(NodeSource.legacyMode(nodeId, sourceId, null, null, null, Instant.now()));

        jdbcTemplate.update("DELETE FROM nodes WHERE id = ?", nodeId);

        assertThat(nodeSourceRepository.findByIds(nodeId, sourceId)).isEmpty();
    }

    @Test
    void sourceDeletion_cascadesLinks() {
        nodeSourceRepository.save(NodeSource.legacyMode(nodeId, sourceId, null, null, null, Instant.now()));

        jdbcTemplate.update("DELETE FROM sources WHERE id = ?", sourceId);

        assertThat(nodeSourceRepository.findByIds(nodeId, sourceId)).isEmpty();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "u-" + id, id + "@e.com"
        );
        return id;
    }

    private UUID insertTopic(UUID creator) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                id, "T", creator
        );
        return id;
    }

    private UUID insertNode(UUID topic, UUID creator) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(), creator, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertSource() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title) VALUES (?, ?, ?)",
                id, SourceType.BOOK.name(), "title"
        );
        return id;
    }

    // ADR-028 academic citation - structured CitationDetail through 9 LEFT JOIN

    @Test
    void findByNodeIdWithLocation_fullAcademicData_returnsAllFields() {
        UUID muhaqqiqId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_muhaqqiqs (id, name, full_name) VALUES (?, ?, ?)",
                muhaqqiqId, "السلامة", "سامي بن محمد السلامة"
        );
        UUID publisherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
                publisherId, "Дар Тайба"
        );
        UUID placeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_publication_places (id, name) VALUES (?, ?)",
                placeId, "Эр-Рияд"
        );
        UUID authorityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO authorities (id, name, full_name, death_year_hijri, created_at) "
                        + "VALUES (?, ?, ?, ?, now())",
                authorityId, "ابن كثير", "إسماعيل بن عمر بن كثير الدمشقي", 774
        );
        UUID bookId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_books (id, book_type, title, authority_id, language, created_by, "
                        + "muhaqqiq_id, publisher_id, publication_place_id, "
                        + "edition_number, published_year_hijri, published_year_gregorian) "
                        + "VALUES (?, 'BOOK', ?, ?, 'ar', ?, ?, ?, ?, ?, ?, ?)",
                bookId, "تفسير القرآن العظيم", authorityId, userId,
                muhaqqiqId, publisherId, placeId, 2, 1420, 1999
        );
        UUID pageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_pages (id, book_id, page_number, text_content, printed_page, part) "
                        + "VALUES (?, ?, ?, 'page text', ?, ?)",
                pageId, bookId, 1, "145", "1"
        );
        UUID sourceWithBookId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title, citation, book_id) "
                        + "VALUES (?, 'BOOK', ?, 'short', ?)",
                sourceWithBookId, "Тафсир", bookId
        );
        NodeSource link = NodeSource.textMode(
                nodeId, sourceWithBookId, "цитата", "контекст", "snapshot location",
                pageId, 100, 200, Instant.now()
        );
        nodeSourceRepository.save(link);

        var rows = nodeSourceRepository.findByNodeIdWithLocation(nodeId);

        assertThat(rows).hasSize(1);
        var c = rows.get(0).citation();
        assertThat(c.authorityId()).isEqualTo(authorityId);
        assertThat(c.authorFullName()).isEqualTo("إسماعيل بن عمر بن كثير الدمشقي");
        assertThat(c.authorDeathYearHijri()).isEqualTo(774);
        assertThat(c.bookTitle()).isEqualTo("تفسير القرآن العظيم");
        assertThat(c.muhaqqiqName()).isEqualTo("السلامة");
        assertThat(c.muhaqqiqFullName()).isEqualTo("سامي بن محمد السلامة");
        assertThat(c.publisherName()).isEqualTo("Дар Тайба");
        assertThat(c.publicationPlaceName()).isEqualTo("Эр-Рияд");
        assertThat(c.editionNumber()).isEqualTo(2);
        assertThat(c.publishedYearHijri()).isEqualTo(1420);
        assertThat(c.publishedYearGregorian()).isEqualTo(1999);
        assertThat(c.part()).isEqualTo("1");
        assertThat(c.printedPage()).isEqualTo("145");
    }

    @Test
    void findByNodeIdWithLocation_partialAcademicData_returnsNullsForMissing() {
        UUID publisherId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_publishers (id, name) VALUES (?, ?)",
                publisherId, "Дар аль-Фикр"
        );
        UUID bookId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_books (id, book_type, title, language, created_by, publisher_id) "
                        + "VALUES (?, 'BOOK', ?, 'ar', ?, ?)",
                bookId, "Книга без полной инфы", userId, publisherId
        );
        UUID pageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_pages (id, book_id, page_number, text_content) "
                        + "VALUES (?, ?, 1, 'text')",
                pageId, bookId
        );
        UUID srcId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title, citation, book_id) "
                        + "VALUES (?, 'BOOK', 'src', 'cit', ?)",
                srcId, bookId
        );
        nodeSourceRepository.save(NodeSource.textMode(
                nodeId, srcId, "q", "c", "loc", pageId, 0, 5, Instant.now()
        ));

        var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

        assertThat(c.publisherName()).isEqualTo("Дар аль-Фикр");
        assertThat(c.muhaqqiqId()).isNull();
        assertThat(c.muhaqqiqName()).isNull();
        assertThat(c.publicationPlaceId()).isNull();
        assertThat(c.editionNumber()).isNull();
        assertThat(c.authorityId()).isNull();
        assertThat(c.authorFullName()).isNull();
    }

    @Test
    void findByNodeIdWithLocation_sourceWithoutBook_returnsCitationWithNulls() {
        UUID srcId = insertSource();
        nodeSourceRepository.save(NodeSource.legacyMode(
                nodeId, srcId, "q", "c", "стр. 42", Instant.now()
        ));

        var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

        assertThat(c.bookId()).isNull();
        assertThat(c.bookTitle()).isNull();
        assertThat(c.publisherName()).isNull();
        assertThat(c.authorityId()).isNull();
        assertThat(c.pageId()).isNull();
    }

    @Test
    void findByNodeIdWithLocation_pdfMode_populatesPdfFields() {
        UUID srcId = insertSource();
        UUID pdfFileId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO library_files (file_id, source_type, content_hash, bucket, storage_key, "
                        + "size_bytes, downloaded_at) "
                        + "VALUES (?, 'SHAMELA', 'hashval', 'library-imported-books', 'k', 1, now())",
                pdfFileId
        );
        nodeSourceRepository.save(NodeSource.pdfMode(
                nodeId, srcId, "q", "c", "PDF стр.50",
                pdfFileId, 50, "{}", Instant.now()
        ));

        var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

        assertThat(c.pdfFileId()).isEqualTo(pdfFileId);
        assertThat(c.pdfPageNumber()).isEqualTo(50);
        assertThat(c.pageId()).isNull();
        assertThat(c.imageRegionId()).isNull();
    }

    @Test
    void findByNodeIdWithLocation_regionMode_populatesRegionAndPagePrintedNumber() {
        UUID bookId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_books (id, book_type, title, language, created_by) "
                        + "VALUES (?, 'BOOK', 'title', 'ar', ?)",
                bookId, userId
        );
        UUID pageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_pages (id, book_id, page_number, image_url, printed_page) "
                        + "VALUES (?, ?, 7, 'img.url', '13')",
                pageId, bookId
        );
        UUID regionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO lib_image_regions (id, page_id, x, y, width, height) "
                        + "VALUES (?, ?, 0.1, 0.1, 0.3, 0.2)",
                regionId, pageId
        );
        UUID srcId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title, citation, book_id) "
                        + "VALUES (?, 'BOOK', 'src', 'cit', ?)",
                srcId, bookId
        );
        nodeSourceRepository.save(NodeSource.regionMode(
                nodeId, srcId, "q", "c", "region loc", regionId, Instant.now()
        ));

        var c = nodeSourceRepository.findByNodeIdWithLocation(nodeId).get(0).citation();

        assertThat(c.imageRegionId()).isEqualTo(regionId);
        assertThat(c.regionPrintedPage()).isEqualTo("13");
        assertThat(c.regionPageNumber()).isEqualTo(7);
        assertThat(c.pageId()).isNull();
    }
}
