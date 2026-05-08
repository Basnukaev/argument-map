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
        NodeSource link = new NodeSource(
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
        NodeSource link = new NodeSource(nodeId, sourceId, "q", "c", null, Instant.now());

        nodeSourceRepository.save(link);

        var reloaded = nodeSourceRepository.findByIds(nodeId, sourceId).orElseThrow();
        assertThat(reloaded.location()).isNull();
    }

    @Test
    void findByNodeId_returnsAllLinksForNode() {
        UUID source2 = insertSource();
        nodeSourceRepository.save(new NodeSource(nodeId, sourceId, "a", null, null, Instant.now()));
        nodeSourceRepository.save(new NodeSource(nodeId, source2, "b", null, null, Instant.now()));

        List<NodeSource> links = nodeSourceRepository.findByNodeId(nodeId);

        assertThat(links).hasSize(2);
    }

    @Test
    void findBySourceId_returnsAllNodesUsingSource() {
        UUID node2 = insertNode(topicId, userId);
        nodeSourceRepository.save(new NodeSource(nodeId, sourceId, "a", null, null, Instant.now()));
        nodeSourceRepository.save(new NodeSource(node2, sourceId, "b", null, null, Instant.now()));

        List<NodeSource> links = nodeSourceRepository.findBySourceId(sourceId);

        assertThat(links).extracting(NodeSource::nodeId)
                .containsExactlyInAnyOrder(nodeId, node2);
    }

    @Test
    void delete_removesLink() {
        nodeSourceRepository.save(new NodeSource(nodeId, sourceId, null, null, null, Instant.now()));

        boolean deleted = nodeSourceRepository.delete(nodeId, sourceId);

        assertThat(deleted).isTrue();
        assertThat(nodeSourceRepository.findByIds(nodeId, sourceId)).isEmpty();
    }

    @Test
    void nodeDeletion_cascadesLinks() {
        nodeSourceRepository.save(new NodeSource(nodeId, sourceId, null, null, null, Instant.now()));

        jdbcTemplate.update("DELETE FROM nodes WHERE id = ?", nodeId);

        assertThat(nodeSourceRepository.findByIds(nodeId, sourceId)).isEmpty();
    }

    @Test
    void sourceDeletion_cascadesLinks() {
        nodeSourceRepository.save(new NodeSource(nodeId, sourceId, null, null, null, Instant.now()));

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
}
