package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.UnsupportedExportFormatException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.AuthorityData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.BookRef;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.EdgeData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.NodeData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.NodeSourceData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.SourceData;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.TopicData;
import ru.basnukaev.argumentmap.web.dto.TopicImportResponse;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicImportServiceIT {

    @Autowired private TopicExportService exportService;
    @Autowired private TopicImportService importService;
    @Autowired private TopicRepository topicRepository;
    @Autowired private NodeRepository nodeRepository;
    @Autowired private EdgeRepository edgeRepository;
    @Autowired private NodeSourceRepository nodeSourceRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private AuthorityRepository authorityRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID importingUserId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        importingUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "u-" + userId, userId + "@e.com");
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                importingUserId, "u-" + importingUserId, importingUserId + "@e.com");
    }

    @Test
    void importTopic_invalidFormatVersion_throws() {
        TopicExportDto dto = new TopicExportDto(
                "999.999", Instant.now(),
                new TopicData(UUID.randomUUID(), "T", null, null, userId, Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        assertThatThrownBy(() -> importService.importTopic(dto, importingUserId))
                .isInstanceOf(UnsupportedExportFormatException.class)
                .hasMessageContaining("999.999");
    }

    @Test
    void importTopic_emptyNodesAndEdges_succeeds() {
        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(UUID.randomUUID(), "Пустая", "desc", null, userId, Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        TopicImportResponse response = importService.importTopic(dto, importingUserId);

        Topic imported = topicRepository.findById(response.topicId()).orElseThrow();
        assertThat(imported.title()).isEqualTo("Пустая");
        assertThat(imported.createdBy()).isEqualTo(importingUserId);
        assertThat(imported.rootNodeId()).isNull();
        assertThat(response.importedNodes()).isZero();
        assertThat(response.importedEdges()).isZero();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void importTopic_freshInstance_createsNewTopicWithRemappedUUIDs() {
        // Build a full export by manual DTO construction
        UUID origTopicId = UUID.randomUUID();
        UUID origRootId = UUID.randomUUID();
        UUID origClaimId = UUID.randomUUID();
        UUID origEdgeId = UUID.randomUUID();
        UUID origAuthId = UUID.randomUUID();
        UUID origSrcId = UUID.randomUUID();
        UUID origNsId = UUID.randomUUID();

        Instant t = Instant.parse("2026-01-01T10:00:00Z");

        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(origTopicId, "Тема из экспорта", null, origRootId, userId, t),
                List.of(
                        new NodeData(origRootId, origTopicId, "QUESTION", "Корневой вопрос?",
                                "UNVERIFIED", null, null, userId, t, t),
                        new NodeData(origClaimId, origTopicId, "CLAIM", "Утверждение",
                                "STANDING", 100.0, 200.0, userId, t, t)
                ),
                List.of(new EdgeData(origEdgeId, origClaimId, origRootId, "RESPONDS_TO",
                        "потому что", "right", "left", userId, t)),
                List.of(new NodeSourceData(origNsId, origClaimId, origSrcId,
                        "цитата", "контекст", "стр. 5",
                        null, null, null,
                        null, null, null,
                        null, t)),
                List.of(new SourceData(origSrcId, "BOOK", "Книга-источник", "citation",
                        null, origAuthId, null, null, t)),
                List.of(new AuthorityData(origAuthId, "Имам Малик", null, "ранний",
                        "малики", null, t, "Малик ибн Анас", 179)),
                List.of()
        );

        TopicImportResponse response = importService.importTopic(dto, importingUserId);

        // Все ID пере-mapping'аются
        assertThat(response.topicId()).isNotEqualTo(origTopicId);
        Topic newTopic = topicRepository.findById(response.topicId()).orElseThrow();
        assertThat(newTopic.title()).isEqualTo("Тема из экспорта");
        assertThat(newTopic.createdBy()).isEqualTo(importingUserId);
        assertThat(newTopic.rootNodeId()).isNotNull().isNotEqualTo(origRootId);

        // 2 узла с новыми ID, остающиеся consistent с edges
        List<Node> nodes = nodeRepository.findByTopicId(newTopic.id());
        assertThat(nodes).hasSize(2);
        assertThat(nodes).extracting(Node::id).doesNotContain(origRootId, origClaimId);

        // Один из 2 узлов должен совпадать с новым rootNodeId
        assertThat(nodes).extracting(Node::id).contains(newTopic.rootNodeId());

        // Edge FK консистентны
        List<Edge> edges = edgeRepository.findByTopicId(newTopic.id());
        assertThat(edges).hasSize(1);
        Edge e = edges.get(0);
        assertThat(nodes).extracting(Node::id).contains(e.fromNodeId(), e.toNodeId());

        // node_source ссылается на новые ID
        assertThat(response.importedNodeSources()).isEqualTo(1);

        // authority + source созданы
        assertThat(response.importedAuthorities()).isEqualTo(1);
        assertThat(response.importedSources()).isEqualTo(1);

        // warnings пустые
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void importTopic_missingBook_addsWarningButImportsSourceWithoutBookId() {
        UUID origTopicId = UUID.randomUUID();
        UUID origNodeId = UUID.randomUUID();
        UUID origSrcId = UUID.randomUUID();
        UUID missingBookId = UUID.randomUUID();
        Instant t = Instant.now();

        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(origTopicId, "T", null, origNodeId, userId, t),
                List.of(new NodeData(origNodeId, origTopicId, "QUESTION", "?",
                        "UNVERIFIED", null, null, userId, t, t)),
                List.of(),
                List.of(),
                List.of(new SourceData(origSrcId, "BOOK", "Книга в экспорте", "cite",
                        null, null, missingBookId, null, t)),
                List.of(),
                List.of(new BookRef(missingBookId, "Книга в экспорте", null))
        );

        TopicImportResponse response = importService.importTopic(dto, importingUserId);

        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings()).anyMatch(w -> w.contains(missingBookId.toString()));

        // Source создан без bookId
        Topic newTopic = topicRepository.findById(response.topicId()).orElseThrow();
        List<Source> allSources = sourceRepository.findAll();
        assertThat(allSources).extracting(Source::title).contains("Книга в экспорте");
        Source imported = allSources.stream()
                .filter(s -> "Книга в экспорте".equals(s.title())).findFirst().orElseThrow();
        assertThat(imported.bookId()).isNull();
    }

    @Test
    void importTopic_existingAuthorityByName_reusesNotDuplicates() {
        // Pre-existing authority с тем же именем
        UUID preAuthId = UUID.randomUUID();
        authorityRepository.save(new Authority(preAuthId, "Имам Шафии",
                null, "ранний", "шафии", null, Instant.now(),
                "Мухаммад ибн Идрис аш-Шафии", 204, null));

        UUID origAuthId = UUID.randomUUID();
        Instant t = Instant.now();

        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(UUID.randomUUID(), "T", null, null, userId, t),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new AuthorityData(origAuthId, "Имам Шафии", null, "ранний",
                        "шафии", null, t, "Шафии full", 204)),
                List.of()
        );

        TopicImportResponse response = importService.importTopic(dto, importingUserId);

        // Не создан новый - re-used existing
        assertThat(response.importedAuthorities()).isEqualTo(1);
        List<Authority> all = authorityRepository.searchByName("Имам Шафии");
        assertThat(all).hasSize(1); // не появилось дубликата
        assertThat(all.get(0).id()).isEqualTo(preAuthId);
    }

    @Test
    void importTopic_roundTrip_exportImportProducesEquivalentTree() {
        // 1. Создать тему руками
        UUID origTopicId = UUID.randomUUID();
        Topic origTopic = new Topic(origTopicId, "Round trip тема",
                "описание", null, userId, Instant.now(),
                ru.basnukaev.argumentmap.domain.TopicVisibility.PRIVATE,
                ru.basnukaev.argumentmap.domain.StatusAlgorithm.MVP);
        topicRepository.save(origTopic);

        UUID rootId = UUID.randomUUID();
        Node root = new Node(rootId, origTopicId, NodeType.QUESTION, "Root question?",
                NodeStatus.UNVERIFIED, null, null, 0, userId, Instant.now(), Instant.now(),
                null);
        nodeRepository.save(root);
        topicRepository.updateRootNodeId(origTopicId, rootId);

        UUID claimId = UUID.randomUUID();
        Node claim = new Node(claimId, origTopicId, NodeType.CLAIM, "Утверждение",
                NodeStatus.STANDING, 50.0, 75.0, 0, userId, Instant.now(), Instant.now(),
                null);
        nodeRepository.save(claim);

        UUID edgeId = UUID.randomUUID();
        Edge edge = new Edge(edgeId, claimId, rootId, EdgeType.RESPONDS_TO,
                "потому что", "right", "left", userId, Instant.now());
        edgeRepository.save(edge);

        // Authority + source + node_source
        UUID authId = UUID.randomUUID();
        Authority auth = new Authority(authId, "Round-trip автор", null, "эпоха",
                "школа", null, Instant.now(), null, null, null);
        authorityRepository.save(auth);

        UUID srcId = UUID.randomUUID();
        Source src = new Source(srcId, SourceType.BOOK, "Round-trip книга", "cite",
                null, authId, null, null, Instant.now());
        sourceRepository.save(src);

        NodeSource ns = NodeSource.legacyMode(claimId, srcId, "quote", "ctx", "loc",
                Instant.now());
        nodeSourceRepository.save(ns);

        // 2. Export
        TopicExportDto dto = exportService.exportTopic(origTopicId);
        assertThat(dto.nodes()).hasSize(2);
        assertThat(dto.edges()).hasSize(1);
        assertThat(dto.nodeSources()).hasSize(1);
        assertThat(dto.sources()).hasSize(1);
        assertThat(dto.authorities()).hasSize(1);

        // 3. Import
        TopicImportResponse response = importService.importTopic(dto, importingUserId);

        // 4. Verify equivalent tree (структурно)
        Topic imported = topicRepository.findById(response.topicId()).orElseThrow();
        assertThat(imported.title()).isEqualTo("Round trip тема");
        assertThat(imported.description()).isEqualTo("описание");

        List<Node> importedNodes = nodeRepository.findByTopicId(imported.id());
        assertThat(importedNodes).hasSize(2);
        assertThat(importedNodes).extracting(Node::content)
                .containsExactlyInAnyOrder("Root question?", "Утверждение");
        assertThat(importedNodes).extracting(Node::nodeType)
                .containsExactlyInAnyOrder(NodeType.QUESTION, NodeType.CLAIM);

        List<Edge> importedEdges = edgeRepository.findByTopicId(imported.id());
        assertThat(importedEdges).hasSize(1);
        assertThat(importedEdges.get(0).edgeType()).isEqualTo(EdgeType.RESPONDS_TO);
        assertThat(importedEdges.get(0).rationale()).isEqualTo("потому что");

        // Сохранён coordinate
        Node importedClaim = importedNodes.stream()
                .filter(n -> n.nodeType() == NodeType.CLAIM).findFirst().orElseThrow();
        assertThat(importedClaim.posX()).isEqualTo(50.0);
        assertThat(importedClaim.posY()).isEqualTo(75.0);
        // createdBy перезаписан на импортирующего
        assertThat(importedClaim.createdBy()).isEqualTo(importingUserId);

        // node_source перенесён + ссылается на новые ID
        List<NodeSource> nss = nodeSourceRepository.findByNodeId(importedClaim.id());
        assertThat(nss).hasSize(1);
        assertThat(nss.get(0).quote()).isEqualTo("quote");
    }

    @Test
    void importTopic_nullTopic_throws() {
        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        assertThatThrownBy(() -> importService.importTopic(dto, importingUserId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importTopic_existingBook_reusesItAndPreservesBookIdOnSource() {
        // Pre-existing book на том же UUID что в экспорте - симулирует обмен
        // темами между инстансами где общая библиотека (или backup/restore
        // того же инстанса)
        UUID bookId = UUID.randomUUID();
        bookRepository.save(new Book(bookId, BookType.BOOK, "Существующая книга",
                null, "ar", null, null, userId, Instant.now(), Instant.now(),
                null, null, null, null, null, null, BookVisibility.PUBLIC));

        UUID origTopicId = UUID.randomUUID();
        UUID origSrcId = UUID.randomUUID();
        Instant t = Instant.now();

        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(origTopicId, "T", null, null, userId, t),
                List.of(), List.of(), List.of(),
                List.of(new SourceData(origSrcId, "BOOK", "Source ref на книгу",
                        "cite", null, null, bookId, null, t)),
                List.of(),
                List.of(new BookRef(bookId, "Существующая книга", null))
        );

        TopicImportResponse response = importService.importTopic(dto, importingUserId);

        // Warning не должен возникнуть - книга найдена
        assertThat(response.warnings())
                .noneMatch(w -> w.contains(bookId.toString()));

        // Source имеет bookId
        Source imported = sourceRepository.findAll().stream()
                .filter(s -> "Source ref на книгу".equals(s.title())).findFirst().orElseThrow();
        assertThat(imported.bookId()).isEqualTo(bookId);
    }
}
