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
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Topic;

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
                Instant.now().truncatedTo(ChronoUnit.MICROS)
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
        Topic older = new Topic(UUID.randomUUID(), "Older", null, null, userId, now.minusSeconds(60));
        Topic newer = new Topic(UUID.randomUUID(), "Newer", null, null, userId, now);
        topicRepository.save(newer);
        topicRepository.save(older);

        List<Topic> topics = topicRepository.findAll();

        assertThat(topics).extracting(Topic::id).containsExactly(older.id(), newer.id());
    }

    @Test
    void updateRootNodeId_setsFkToNode() {
        Topic topic = new Topic(UUID.randomUUID(), "T", null, null, userId, Instant.now());
        topicRepository.save(topic);
        UUID nodeId = insertNode(topic.id());

        topicRepository.updateRootNodeId(topic.id(), nodeId);

        Topic reloaded = topicRepository.findById(topic.id()).orElseThrow();
        assertThat(reloaded.rootNodeId()).isEqualTo(nodeId);
    }

    @Test
    void deleteById_removesTopic_andCascadesNodes() {
        Topic topic = new Topic(UUID.randomUUID(), "T", null, null, userId, Instant.now());
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

    private UUID insertNode(UUID topicId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, weight, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.QUESTION.name(), "?", NodeStatus.UNVERIFIED.name(),
                5, userId, odt(now), odt(now)
        );
        return id;
    }
}
