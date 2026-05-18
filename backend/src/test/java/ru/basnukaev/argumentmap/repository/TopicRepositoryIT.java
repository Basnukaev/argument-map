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
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.StatusAlgorithm;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicVisibility;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicRepositoryIT {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    @Test
    void save_insertsTopic_findByIdReturnsSame() {
        Topic topic = new Topic(
                UUID.randomUUID(),
                "Мавлид это бид'а?",
                "Разбор аргументов сторон",
                null,
                userId,
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                TopicVisibility.PRIVATE,
                StatusAlgorithm.MVP
        );

        topicRepository.save(topic);

        Optional<Topic> found = topicRepository.findById(topic.id());
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Мавлид это бид'а?");
        assertThat(found.get().description()).isEqualTo("Разбор аргументов сторон");
        assertThat(found.get().rootNodeId()).isNull();
        assertThat(found.get().createdBy()).isEqualTo(userId);
        assertThat(found.get().createdAt()).isEqualTo(topic.createdAt());
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        assertThat(topicRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsAllTopicsOrderedByCreatedAt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Topic older = new Topic(UUID.randomUUID(), "Older", null, null, userId, now.minusSeconds(60), TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        Topic newer = new Topic(UUID.randomUUID(), "Newer", null, null, userId, now, TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        topicRepository.save(newer);
        topicRepository.save(older);

        List<Topic> topics = topicRepository.findAll();

        assertThat(topics).extracting(Topic::id).containsExactly(older.id(), newer.id());
    }

    @Test
    void updateRootNodeId_setsFkToNode() {
        Topic topic = new Topic(UUID.randomUUID(), "T", null, null, userId, Instant.now(), TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        topicRepository.save(topic);
        UUID nodeId = insertNode(topic.id());

        topicRepository.updateRootNodeId(topic.id(), nodeId);

        Topic reloaded = topicRepository.findById(topic.id()).orElseThrow();
        assertThat(reloaded.rootNodeId()).isEqualTo(nodeId);
    }

    @Test
    void deleteById_removesTopic_andCascadesNodes() {
        Topic topic = new Topic(UUID.randomUUID(), "T", null, null, userId, Instant.now(), TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        topicRepository.save(topic);
        UUID nodeId = insertNode(topic.id());

        boolean deleted = topicRepository.deleteById(topic.id());

        assertThat(deleted).isTrue();
        assertThat(topicRepository.findById(topic.id())).isEmpty();
        Integer remainingNodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nodes WHERE id = ?", Integer.class, nodeId
        );
        assertThat(remainingNodes).isZero();
    }

    @Test
    void deleteById_whenNotExists_returnsFalse() {
        assertThat(topicRepository.deleteById(UUID.randomUUID())).isFalse();
    }

    @Test
    void findAllWithCounts_returnsTopicsWithNodeAndEdgeAggregates() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Topic topicA = new Topic(UUID.randomUUID(), "A", null, null, userId, now.minusSeconds(60), TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        Topic topicB = new Topic(UUID.randomUUID(), "B", null, null, userId, now, TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        topicRepository.save(topicA);
        topicRepository.save(topicB);

        // тема A: 3 узла, 2 ребра
        UUID a1 = insertNode(topicA.id());
        UUID a2 = insertNode(topicA.id());
        UUID a3 = insertNode(topicA.id());
        insertEdge(a1, a2);
        insertEdge(a2, a3);

        // тема B: 1 узел, 0 рёбер
        insertNode(topicB.id());

        List<TopicWithCounts> result = topicRepository.findAllWithCounts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(twc -> twc.topic().id()).containsExactly(topicA.id(), topicB.id());
        assertThat(result.get(0).nodeCount()).isEqualTo(3);
        assertThat(result.get(0).edgeCount()).isEqualTo(2);
        assertThat(result.get(1).nodeCount()).isEqualTo(1);
        assertThat(result.get(1).edgeCount()).isZero();
    }

    @Test
    void findAllWithCounts_topicWithoutNodesReturnsZeroCounts() {
        Topic empty = new Topic(UUID.randomUUID(), "Пустая", null, null, userId, Instant.now(), TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        topicRepository.save(empty);

        List<TopicWithCounts> result = topicRepository.findAllWithCounts();

        TopicWithCounts found = result.stream()
                .filter(twc -> twc.topic().id().equals(empty.id()))
                .findFirst()
                .orElseThrow();
        assertThat(found.nodeCount()).isZero();
        assertThat(found.edgeCount()).isZero();
    }

    @Test
    void findByIdWithCounts_returnsAggregatesForOneTopic() {
        Topic topic = new Topic(UUID.randomUUID(), "T", null, null, userId, Instant.now(), TopicVisibility.PRIVATE, StatusAlgorithm.MVP);
        topicRepository.save(topic);
        UUID n1 = insertNode(topic.id());
        UUID n2 = insertNode(topic.id());
        insertEdge(n1, n2);

        Optional<TopicWithCounts> result = topicRepository.findByIdWithCounts(topic.id());

        assertThat(result).isPresent();
        assertThat(result.get().topic().id()).isEqualTo(topic.id());
        assertThat(result.get().nodeCount()).isEqualTo(2);
        assertThat(result.get().edgeCount()).isEqualTo(1);
    }

    @Test
    void findByIdWithCounts_whenNotExists_returnsEmpty() {
        assertThat(topicRepository.findByIdWithCounts(UUID.randomUUID())).isEmpty();
    }

    private UUID insertNode(UUID topicId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.QUESTION.name(), "?", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertEdge(UUID fromNodeId, UUID toNodeId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO edges (id, from_node_id, to_node_id, edge_type, "
                        + "created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, fromNodeId, toNodeId, EdgeType.SUPPORTS.name(), userId, odt(now)
        );
        return id;
    }
}
