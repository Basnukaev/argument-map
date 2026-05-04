package ru.basnukaev.argumentmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GraphServiceIT {

    @Autowired
    private GraphService graphService;

    @Autowired
    private TopicService topicService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private EdgeService edgeService;

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
    void getGraph_returnsTopicNodesAndEdges() {
        Topic topic = topicService.createTopic("T", null, "Корневой вопрос?", userId);
        Node claim = nodeService.createNode(topic.id(), NodeType.CLAIM, "Тезис", userId);
        Node argument = nodeService.createNode(topic.id(), NodeType.ARGUMENT, "Довод", userId);
        Edge support = edgeService.createEdge(argument.id(), claim.id(), EdgeType.SUPPORTS, null, userId);

        GraphView graph = graphService.getGraph(topic.id());

        assertThat(graph.topic().id()).isEqualTo(topic.id());
        assertThat(graph.topic().rootNodeId()).isEqualTo(topic.rootNodeId());
        assertThat(graph.nodes()).extracting(Node::id)
                .containsExactlyInAnyOrder(topic.rootNodeId(), claim.id(), argument.id());
        assertThat(graph.edges()).extracting(Edge::id).containsExactly(support.id());
    }

    @Test
    void getGraph_returnsEmptyNodesAndEdges_forFreshTopic_exceptRoot() {
        Topic topic = topicService.createTopic("Empty", null, "Q?", userId);

        GraphView graph = graphService.getGraph(topic.id());

        assertThat(graph.nodes()).hasSize(1);
        assertThat(graph.nodes().get(0).id()).isEqualTo(topic.rootNodeId());
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void getGraph_whenTopicNotFound_throws() {
        assertThatThrownBy(() -> graphService.getGraph(UUID.randomUUID()))
                .isInstanceOf(TopicNotFoundException.class);
    }
}
