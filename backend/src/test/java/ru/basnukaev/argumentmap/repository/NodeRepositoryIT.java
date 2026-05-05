package ru.basnukaev.argumentmap.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeRepositoryIT {

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                topicId, "T", userId
        );
    }

    @Test
    void save_insertsNode_andFindByIdReturnsIt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Node node = new Node(
                UUID.randomUUID(), topicId, NodeType.CLAIM,
                "Мавлид допустим", NodeStatus.STANDING,
                null, null,
                userId, now, now
        );

        nodeRepository.save(node);

        Optional<Node> found = nodeRepository.findById(node.id());
        assertThat(found).isPresent();
        Node reloaded = found.get();
        assertThat(reloaded.topicId()).isEqualTo(topicId);
        assertThat(reloaded.nodeType()).isEqualTo(NodeType.CLAIM);
        assertThat(reloaded.content()).isEqualTo("Мавлид допустим");
        assertThat(reloaded.status()).isEqualTo(NodeStatus.STANDING);
        assertThat(reloaded.posX()).isNull();
        assertThat(reloaded.posY()).isNull();
        assertThat(reloaded.createdAt()).isEqualTo(now);
        assertThat(reloaded.updatedAt()).isEqualTo(now);
    }

    @Test
    void updatePosition_persistsCoordinates_andDoesNotTouchUpdatedAt() {
        Instant created = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        UUID nodeId = insertNode(topicId, "x", created);

        boolean updated = nodeRepository.updatePosition(nodeId, 123.45, -67.89);

        assertThat(updated).isTrue();
        Node reloaded = nodeRepository.findById(nodeId).orElseThrow();
        assertThat(reloaded.posX()).isEqualTo(123.45);
        assertThat(reloaded.posY()).isEqualTo(-67.89);
        assertThat(reloaded.updatedAt()).isEqualTo(created);
    }

    @Test
    void updatePosition_returnsFalse_whenNodeNotFound() {
        boolean updated = nodeRepository.updatePosition(UUID.randomUUID(), 0.0, 0.0);
        assertThat(updated).isFalse();
    }

    @Test
    void findByTopicId_returnsOnlyTopicNodes_orderedByCreatedAt() {
        UUID otherTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                otherTopicId, "Other", userId
        );
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID n1 = insertNode(topicId, "first", base.minusSeconds(10));
        UUID n2 = insertNode(topicId, "second", base);
        insertNode(otherTopicId, "foreign", base);

        List<Node> nodes = nodeRepository.findByTopicId(topicId);

        assertThat(nodes).extracting(Node::id).containsExactly(n1, n2);
    }

    @Test
    void update_changesContentStatusAndTimestamp() {
        Instant created = Instant.now().minusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        UUID nodeId = insertNode(topicId, "old", created);

        Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Node updated = new Node(
                nodeId, topicId, NodeType.QUESTION,
                "new content", NodeStatus.DISPUTED,
                null, null,
                userId, created, updatedAt
        );
        nodeRepository.update(updated);

        Node reloaded = nodeRepository.findById(nodeId).orElseThrow();
        assertThat(reloaded.content()).isEqualTo("new content");
        assertThat(reloaded.status()).isEqualTo(NodeStatus.DISPUTED);
        assertThat(reloaded.updatedAt()).isEqualTo(updatedAt);
        assertThat(reloaded.createdAt()).isEqualTo(created);
    }

    @Test
    void updateStatus_changesOnlyStatusAndTimestamp() {
        Instant created = Instant.now().minusSeconds(100).truncatedTo(ChronoUnit.MICROS);
        UUID nodeId = insertNode(topicId, "content", created);

        Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        nodeRepository.updateStatus(nodeId, NodeStatus.REFUTED, updatedAt);

        Node reloaded = nodeRepository.findById(nodeId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(NodeStatus.REFUTED);
        assertThat(reloaded.content()).isEqualTo("content");
        assertThat(reloaded.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void deleteById_removesNode() {
        UUID nodeId = insertNode(topicId, "x", Instant.now());

        boolean deleted = nodeRepository.deleteById(nodeId);

        assertThat(deleted).isTrue();
        assertThat(nodeRepository.findById(nodeId)).isEmpty();
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        assertThat(nodeRepository.findById(UUID.randomUUID())).isEmpty();
    }

    private UUID insertNode(UUID topic, String content, Instant when) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, NodeType.ARGUMENT.name(), content, NodeStatus.UNVERIFIED.name(),
                userId, odt(when), odt(when)
        );
        return id;
    }
}
