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
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EdgeRepositoryIT {

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeA;
    private UUID nodeB;
    private UUID nodeC;

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
        nodeA = insertNode(topicId);
        nodeB = insertNode(topicId);
        nodeC = insertNode(topicId);
    }

    @Test
    void save_insertsEdge_andFindByIdReturnsIt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Edge edge = new Edge(
                UUID.randomUUID(), nodeA, nodeB, EdgeType.SUPPORTS,
                "потому что хадис", null, null, userId, now, 0
        );

        edgeRepository.save(edge);

        Optional<Edge> found = edgeRepository.findById(edge.id());
        assertThat(found).isPresent();
        Edge reloaded = found.get();
        assertThat(reloaded.fromNodeId()).isEqualTo(nodeA);
        assertThat(reloaded.toNodeId()).isEqualTo(nodeB);
        assertThat(reloaded.edgeType()).isEqualTo(EdgeType.SUPPORTS);
        assertThat(reloaded.rationale()).isEqualTo("потому что хадис");
        assertThat(reloaded.sourceHandle()).isNull();
        assertThat(reloaded.targetHandle()).isNull();
        assertThat(reloaded.createdAt()).isEqualTo(now);
    }

    @Test
    void save_withNullRationale_worksFine() {
        Edge edge = new Edge(
                UUID.randomUUID(), nodeA, nodeB, EdgeType.REFUTES,
                null, null, null, userId, Instant.now(), 0
        );
        edgeRepository.save(edge);

        assertThat(edgeRepository.findById(edge.id())).isPresent()
                .get().extracting(Edge::rationale).isNull();
    }

    @Test
    void save_persistsSourceAndTargetHandle() {
        Edge edge = new Edge(
                UUID.randomUUID(), nodeA, nodeB, EdgeType.SUPPORTS,
                null, "right", "left", userId, Instant.now(), 0
        );
        edgeRepository.save(edge);

        Edge reloaded = edgeRepository.findById(edge.id()).orElseThrow();
        assertThat(reloaded.sourceHandle()).isEqualTo("right");
        assertThat(reloaded.targetHandle()).isEqualTo("left");
    }

    @Test
    void findByFromNodeId_returnsOutgoingEdges() {
        UUID e1 = insertEdge(nodeA, nodeB, EdgeType.SUPPORTS);
        UUID e2 = insertEdge(nodeA, nodeC, EdgeType.REFUTES);
        insertEdge(nodeB, nodeC, EdgeType.SUPPORTS);

        List<Edge> edges = edgeRepository.findByFromNodeId(nodeA);

        assertThat(edges).extracting(Edge::id).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void findByToNodeId_returnsIncomingEdges() {
        UUID e1 = insertEdge(nodeA, nodeC, EdgeType.SUPPORTS);
        UUID e2 = insertEdge(nodeB, nodeC, EdgeType.REFUTES);
        insertEdge(nodeA, nodeB, EdgeType.QUALIFIES);

        List<Edge> edges = edgeRepository.findByToNodeId(nodeC);

        assertThat(edges).extracting(Edge::id).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void findByTopicId_returnsOnlyEdgesBelongingToTopic() {
        UUID otherTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                otherTopicId, "Other", userId
        );
        UUID foreignNode = insertNode(otherTopicId);

        UUID e1 = insertEdge(nodeA, nodeB, EdgeType.SUPPORTS);
        UUID e2 = insertEdge(nodeB, nodeC, EdgeType.REFUTES);
        insertEdge(foreignNode, foreignNode, EdgeType.RESPONDS_TO);

        List<Edge> edges = edgeRepository.findByTopicId(topicId);

        assertThat(edges).extracting(Edge::id).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void update_changesAllUpdatableFields() {
        UUID edgeId = UUID.randomUUID();
        Edge original = new Edge(
                edgeId, nodeA, nodeB, EdgeType.SUPPORTS,
                "первое обоснование", "right", "left", userId, Instant.now(), 0
        );
        edgeRepository.save(original);

        Edge updated = new Edge(
                edgeId, nodeA, nodeC, EdgeType.REFUTES,
                "новое обоснование", "bottom", "top", userId, original.createdAt(), 0
        );
        boolean ok = edgeRepository.update(updated);

        assertThat(ok).isTrue();
        Edge reloaded = edgeRepository.findById(edgeId).orElseThrow();
        assertThat(reloaded.fromNodeId()).isEqualTo(nodeA);
        assertThat(reloaded.toNodeId()).isEqualTo(nodeC);
        assertThat(reloaded.edgeType()).isEqualTo(EdgeType.REFUTES);
        assertThat(reloaded.rationale()).isEqualTo("новое обоснование");
        assertThat(reloaded.sourceHandle()).isEqualTo("bottom");
        assertThat(reloaded.targetHandle()).isEqualTo("top");
    }

    @Test
    void update_whenEdgeMissing_returnsFalse() {
        Edge ghost = new Edge(
                UUID.randomUUID(), nodeA, nodeB, EdgeType.SUPPORTS,
                null, null, null, userId, Instant.now(), 0
        );

        assertThat(edgeRepository.update(ghost)).isFalse();
    }

    @Test
    void deleteById_removesEdge() {
        UUID edgeId = insertEdge(nodeA, nodeB, EdgeType.INVALIDATES);

        boolean deleted = edgeRepository.deleteById(edgeId);

        assertThat(deleted).isTrue();
        assertThat(edgeRepository.findById(edgeId)).isEmpty();
    }

    @Test
    void nodeDeletion_cascadesToEdges() {
        UUID edgeId = insertEdge(nodeA, nodeB, EdgeType.SUPPORTS);

        jdbcTemplate.update("DELETE FROM nodes WHERE id = ?", nodeA);

        assertThat(edgeRepository.findById(edgeId)).isEmpty();
    }

    private UUID insertNode(UUID topic) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, NodeType.CLAIM.name(), "content", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertEdge(UUID from, UUID to, EdgeType type) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO edges (id, from_node_id, to_node_id, edge_type, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, from, to, type.name(), userId, odt(Instant.now())
        );
        return id;
    }
}
