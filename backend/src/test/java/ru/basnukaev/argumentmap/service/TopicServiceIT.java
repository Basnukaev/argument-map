package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicServiceIT {

    @Autowired
    private TopicService topicService;

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
    void createTopic_createsTopicWithRootQuestion_inOneTransaction() {
        Topic topic = topicService.createTopic(
                "Мавлид это бид'а?", "Разбор аргументов",
                "Является ли празднование мавлида нововведением?",
                userId
        );

        assertThat(topic.id()).isNotNull();
        assertThat(topic.title()).isEqualTo("Мавлид это бид'а?");
        assertThat(topic.rootNodeId()).isNotNull();

        Integer nodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nodes WHERE topic_id = ? AND node_type = ?",
                Integer.class, topic.id(), NodeType.QUESTION.name()
        );
        assertThat(nodeCount).isOne();

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM nodes WHERE id = ?",
                String.class, topic.rootNodeId()
        );
        assertThat(content).isEqualTo("Является ли празднование мавлида нововведением?");
    }

    @Test
    void getTopic_returnsExistingTopic() {
        Topic created = topicService.createTopic("T", null, "?", userId);

        Topic found = topicService.getTopic(created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.rootNodeId()).isEqualTo(created.rootNodeId());
    }

    @Test
    void getTopic_whenNotFound_throwsTopicNotFoundException() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> topicService.getTopic(missing))
                .isInstanceOf(TopicNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void listTopics_returnsAllCreated() {
        topicService.createTopic("First", null, "?", userId);
        topicService.createTopic("Second", null, "?", userId);

        List<Topic> topics = topicService.listTopics();

        assertThat(topics).hasSize(2);
        assertThat(topics).extracting(Topic::title).containsExactlyInAnyOrder("First", "Second");
    }

    @Test
    void deleteTopic_removesTopicAndCascadesRootNode() {
        Topic topic = topicService.createTopic("T", null, "?", userId);
        UUID rootNodeId = topic.rootNodeId();

        topicService.deleteTopic(topic.id());

        assertThatThrownBy(() -> topicService.getTopic(topic.id()))
                .isInstanceOf(TopicNotFoundException.class);

        Integer remainingNodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nodes WHERE id = ?",
                Integer.class, rootNodeId
        );
        assertThat(remainingNodes).isZero();
    }

    @Test
    void deleteTopic_whenNotFound_throwsTopicNotFoundException() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> topicService.deleteTopic(missing))
                .isInstanceOf(TopicNotFoundException.class);
    }
}
