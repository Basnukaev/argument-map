package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.repository.NodeRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class StatusCalculationServiceIT {

    @Autowired
    private StatusCalculationService service;

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
    void recalculateTopic_persistsStatusChanges() {
        UUID source = insertNode(NodeStatus.STANDING);
        UUID claim = insertNode(NodeStatus.UNVERIFIED);
        insertEdge(source, claim, EdgeType.SUPPORTS);

        service.recalculateTopic(topicId);

        NodeStatus persistedClaim = nodeRepository.findById(claim).orElseThrow().status();
        assertThat(persistedClaim).isEqualTo(NodeStatus.STANDING);

        // source без влияющих рёбер — статус сохраняется
        NodeStatus persistedSource = nodeRepository.findById(source).orElseThrow().status();
        assertThat(persistedSource).isEqualTo(NodeStatus.STANDING);
    }

    @Test
    void recalculateTopic_emptyTopic_doesNotFail() {
        UUID emptyTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                emptyTopicId, "Empty", userId
        );

        service.recalculateTopic(emptyTopicId);
        // Ничего не должно упасть, НИЧЕГО не записывается
    }

    @Test
    void recalculateTopic_noChanges_doesNotWriteToDb() {
        UUID claim = insertNode(NodeStatus.UNVERIFIED);
        UUID stamped = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM nodes WHERE id = ?", java.sql.Timestamp.class, claim
        ) != null ? claim : null;
        // Пересчёт без рёбер — никаких изменений
        service.recalculateTopic(topicId);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nodes WHERE id = ? AND status = 'UNVERIFIED'",
                Integer.class, stamped
        );
        assertThat(count).isOne();
    }

    private UUID insertNode(NodeStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.CLAIM.name(), "c", status.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private void insertEdge(UUID from, UUID to, EdgeType type) {
        jdbcTemplate.update(
                "INSERT INTO edges (id, from_node_id, to_node_id, edge_type, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), from, to, type.name(), userId, odt(Instant.now())
        );
    }
}
