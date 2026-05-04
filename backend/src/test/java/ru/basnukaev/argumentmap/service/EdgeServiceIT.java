package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
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
import ru.basnukaev.argumentmap.exception.EdgeNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidEdgeException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EdgeServiceIT {

    @Autowired
    private EdgeService edgeService;

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeA;
    private UUID nodeB;

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
    }

    @Test
    void createEdge_savesEdgeWithGeneratedId() {
        Edge edge = edgeService.createEdge(nodeA, nodeB, EdgeType.SUPPORTS, "потому что", userId);

        assertThat(edge.id()).isNotNull();
        assertThat(edge.fromNodeId()).isEqualTo(nodeA);
        assertThat(edge.toNodeId()).isEqualTo(nodeB);
        assertThat(edgeRepository.findById(edge.id())).isPresent();
    }

    @Test
    void createEdge_rejectsSelfLoop() {
        assertThatThrownBy(() -> edgeService.createEdge(
                nodeA, nodeA, EdgeType.SUPPORTS, null, userId
        )).isInstanceOf(InvalidEdgeException.class)
          .hasMessageContaining("на себя");
    }

    @Test
    void createEdge_rejectsCrossTopic() {
        UUID otherTopic = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                otherTopic, "Other", userId
        );
        UUID foreign = insertNode(otherTopic);

        assertThatThrownBy(() -> edgeService.createEdge(
                nodeA, foreign, EdgeType.SUPPORTS, null, userId
        )).isInstanceOf(InvalidEdgeException.class)
          .hasMessageContaining("границу темы");
    }

    @Test
    void createEdge_whenFromNodeMissing_throwsNodeNotFound() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> edgeService.createEdge(
                missing, nodeB, EdgeType.SUPPORTS, null, userId
        )).isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void createEdge_whenToNodeMissing_throwsNodeNotFound() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> edgeService.createEdge(
                nodeA, missing, EdgeType.SUPPORTS, null, userId
        )).isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void deleteEdge_removesIt() {
        Edge edge = edgeService.createEdge(nodeA, nodeB, EdgeType.REFUTES, null, userId);

        edgeService.deleteEdge(edge.id());

        assertThat(edgeRepository.findById(edge.id())).isEmpty();
    }

    @Test
    void deleteEdge_whenNotFound_throws() {
        assertThatThrownBy(() -> edgeService.deleteEdge(UUID.randomUUID()))
                .isInstanceOf(EdgeNotFoundException.class);
    }

    @Test
    void createEdge_recalcsStatuses_targetBecomesDisputedWhenStandingSupportAndStandingRefute() {
        // Готовим состояние "вручную" через jdbcTemplate: standing source supports claim
        UUID standingSource = insertNodeWithStatus(topicId, NodeStatus.STANDING);
        UUID claim = insertNodeWithStatus(topicId, NodeStatus.UNVERIFIED);
        edgeService.createEdge(standingSource, claim, EdgeType.SUPPORTS, null, userId);
        // claim стал STANDING после первого ребра
        assertThat(nodeRepository.findById(claim).orElseThrow().status())
                .isEqualTo(NodeStatus.STANDING);

        UUID standingRefuter = insertNodeWithStatus(topicId, NodeStatus.STANDING);
        edgeService.createEdge(standingRefuter, claim, EdgeType.REFUTES, null, userId);

        assertThat(nodeRepository.findById(claim).orElseThrow().status())
                .isEqualTo(NodeStatus.DISPUTED);
    }

    @Test
    void deleteEdge_recalcsStatuses_oneOfMultipleEdgesGone_statusReflectsRemaining() {
        // Сценарий с реальным эффектом: к claim ведут support и refute, оба от STANDING.
        // Пока есть оба — claim DISPUTED. После удаления refute остаётся только support → STANDING.
        UUID standingA = insertNodeWithStatus(topicId, NodeStatus.STANDING);
        UUID standingB = insertNodeWithStatus(topicId, NodeStatus.STANDING);
        UUID claim = insertNodeWithStatus(topicId, NodeStatus.UNVERIFIED);
        edgeService.createEdge(standingA, claim, EdgeType.SUPPORTS, null, userId);
        Edge refuteEdge = edgeService.createEdge(standingB, claim, EdgeType.REFUTES, null, userId);
        assertThat(nodeRepository.findById(claim).orElseThrow().status())
                .isEqualTo(NodeStatus.DISPUTED);

        edgeService.deleteEdge(refuteEdge.id());

        assertThat(nodeRepository.findById(claim).orElseThrow().status())
                .isEqualTo(NodeStatus.STANDING);
    }

    private UUID insertNodeWithStatus(UUID topic, NodeStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, NodeType.CLAIM.name(), "c", status.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertNode(UUID topic) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topic, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }
}
