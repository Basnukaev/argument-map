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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Revision;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeServiceIT {

    @Autowired
    private NodeService nodeService;

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
    void createNode_setsUnverifiedStatusAndTimestamps() {
        Node node = nodeService.createNode(
                topicId, NodeType.CLAIM, "Тезис", 7, userId
        );

        assertThat(node.id()).isNotNull();
        assertThat(node.status()).isEqualTo(NodeStatus.UNVERIFIED);
        assertThat(node.weight()).isEqualTo(7);
        assertThat(node.createdAt()).isNotNull();
        assertThat(node.updatedAt()).isEqualTo(node.createdAt());

        Node persisted = nodeRepository.findById(node.id()).orElseThrow();
        assertThat(persisted.content()).isEqualTo("Тезис");
    }

    @Test
    void createNode_whenTopicNotFound_throws() {
        UUID missingTopic = UUID.randomUUID();

        assertThatThrownBy(() -> nodeService.createNode(
                missingTopic, NodeType.CLAIM, "x", 5, userId
        )).isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void updateContent_writesRevision_withOldAndNewContent() throws InterruptedException {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "старый", 5, userId);
        Thread.sleep(2);  // гарантия отличающегося updated_at

        Node updated = nodeService.updateContent(node.id(), "новый", userId);

        assertThat(updated.content()).isEqualTo("новый");
        assertThat(updated.updatedAt()).isAfter(node.createdAt());

        List<Revision> history = nodeService.getRevisions(node.id());
        assertThat(history).hasSize(1);
        Revision r = history.get(0);
        assertThat(r.contentBefore()).isEqualTo("старый");
        assertThat(r.contentAfter()).isEqualTo("новый");
        assertThat(r.changedBy()).isEqualTo(userId);
    }

    @Test
    void updateContent_multipleEdits_buildsLinearHistory() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "v1", 5, userId);
        nodeService.updateContent(node.id(), "v2", userId);
        nodeService.updateContent(node.id(), "v3", userId);

        List<Revision> history = nodeService.getRevisions(node.id());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).contentBefore()).isEqualTo("v1");
        assertThat(history.get(0).contentAfter()).isEqualTo("v2");
        assertThat(history.get(1).contentBefore()).isEqualTo("v2");
        assertThat(history.get(1).contentAfter()).isEqualTo("v3");
    }

    @Test
    void updateContent_whenNodeNotFound_throws() {
        assertThatThrownBy(() -> nodeService.updateContent(
                UUID.randomUUID(), "x", userId
        )).isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void deleteNode_removesNode() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "x", 5, userId);

        nodeService.deleteNode(node.id());

        assertThat(nodeRepository.findById(node.id())).isEmpty();
    }

    @Test
    void deleteNode_whenNotFound_throws() {
        assertThatThrownBy(() -> nodeService.deleteNode(UUID.randomUUID()))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void getRevisions_whenNodeNotFound_throws() {
        assertThatThrownBy(() -> nodeService.getRevisions(UUID.randomUUID()))
                .isInstanceOf(NodeNotFoundException.class);
    }
}
