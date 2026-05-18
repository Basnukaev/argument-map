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
import ru.basnukaev.argumentmap.exception.NodeIsRootException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeServiceIT {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private TopicRepository topicRepository;

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
                topicId, NodeType.CLAIM, "Тезис", userId
        );

        assertThat(node.id()).isNotNull();
        assertThat(node.status()).isEqualTo(NodeStatus.UNVERIFIED);
        assertThat(node.createdAt()).isNotNull();
        assertThat(node.updatedAt()).isEqualTo(node.createdAt());

        Node persisted = nodeRepository.findById(node.id()).orElseThrow();
        assertThat(persisted.content()).isEqualTo("Тезис");
    }

    @Test
    void createNode_whenTopicNotFound_throws() {
        UUID missingTopic = UUID.randomUUID();

        assertThatThrownBy(() -> nodeService.createNode(
                missingTopic, NodeType.CLAIM, "x", userId
        )).isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void updateContent_writesRevision_withOldAndNewContent() throws InterruptedException {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "старый", userId);
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
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "v1", userId);
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
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "x", userId);

        nodeService.deleteNode(node.id());

        assertThat(nodeRepository.findById(node.id())).isEmpty();
    }

    @Test
    void deleteNode_whenNotFound_throws() {
        assertThatThrownBy(() -> nodeService.deleteNode(UUID.randomUUID()))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void deleteNode_whenRootQuestion_throwsNodeIsRoot() {
        // создаём корневой узел и привязываем его к topic.root_node_id
        Node root = nodeService.createNode(topicId, NodeType.QUESTION, "Корневой вопрос?", userId);
        topicRepository.updateRootNodeId(topicId, root.id());

        assertThatThrownBy(() -> nodeService.deleteNode(root.id()))
                .isInstanceOf(NodeIsRootException.class);

        // узел остался в БД - удаление действительно не произошло
        assertThat(nodeRepository.findById(root.id())).isPresent();
    }

    @Test
    void deleteNode_whenNonRootInTopicWithRoot_succeeds() {
        // sanity: root установлен, удаляем другой (не корневой) узел -
        // должно работать как раньше
        Node root = nodeService.createNode(topicId, NodeType.QUESTION, "Корень?", userId);
        topicRepository.updateRootNodeId(topicId, root.id());
        Node child = nodeService.createNode(topicId, NodeType.CLAIM, "Тезис", userId);

        nodeService.deleteNode(child.id());

        assertThat(nodeRepository.findById(child.id())).isEmpty();
        assertThat(nodeRepository.findById(root.id())).isPresent();
    }

    @Test
    void getRevisions_whenNodeNotFound_throws() {
        assertThatThrownBy(() -> nodeService.getRevisions(UUID.randomUUID()))
                .isInstanceOf(NodeNotFoundException.class);
    }

    @Test
    void createNode_withOriginalLang_persists() {
        // После миграции 45 translations - в child-таблице node_translations.
        // На Node остался только originalLang (свойство оригинала)
        Node node = nodeService.createNode(
                topicId, NodeType.EVIDENCE,
                "إنما الأعمال بالنيات",
                "ar", userId
        );

        Node persisted = nodeRepository.findById(node.id()).orElseThrow();
        assertThat(persisted.content()).isEqualTo("إنما الأعمال بالنيات");
        assertThat(persisted.originalLang()).isEqualTo("ar");
    }

    @Test
    void createNode_invalidOriginalLang_throws() {
        assertThatThrownBy(() -> nodeService.createNode(
                topicId, NodeType.EVIDENCE, "x", "fr", userId
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("originalLang");
    }

    @Test
    void updateContent_clearOriginalLang_setsToNull() {
        Node initial = nodeService.createNode(
                topicId, NodeType.EVIDENCE, "إنما الأعمال", "ar", userId
        );
        // очищаем originalLang через явный null - content не меняем
        nodeService.updateContent(
                initial.id(),
                NodeService.NoChange.INSTANCE,  // content не меняем
                null,                            // originalLang: очистить
                userId
        );

        Node reloaded = nodeRepository.findById(initial.id()).orElseThrow();
        assertThat(reloaded.originalLang()).isNull();
        // content не менялся - revision НЕ должен быть записан
        assertThat(nodeService.getRevisions(initial.id())).isEmpty();
    }

    @Test
    void updateContent_setOriginalLangOnExistingNode_persists() {
        Node initial = nodeService.createNode(topicId, NodeType.CLAIM, "оригинал", userId);

        nodeService.updateContent(
                initial.id(),
                NodeService.NoChange.INSTANCE,
                "ru",
                userId
        );

        Node reloaded = nodeRepository.findById(initial.id()).orElseThrow();
        assertThat(reloaded.originalLang()).isEqualTo("ru");
    }

    @Test
    void deleteNode_triggersStatusRecalc_dependentNodesUpdated() {
        // standingSource (STANDING) → support → claim (DISPUTED через refuter)
        //                                       ← refute от refuter (STANDING)
        // удаляем refuter → каскад удалит ребро → claim должен пересчитаться в STANDING
        UUID standingSource = insertWithStatus(NodeStatus.STANDING);
        UUID refuter = insertWithStatus(NodeStatus.STANDING);
        UUID claim = insertWithStatus(NodeStatus.UNVERIFIED);
        insertEdge(standingSource, claim, "SUPPORTS");
        insertEdge(refuter, claim, "REFUTES");
        // первичный пересчёт делаем "вручную" чтобы привести claim к DISPUTED
        nodeService.deleteNode(insertWithStatus(NodeStatus.UNVERIFIED));  // тригер пересчёта
        assertThat(nodeRepository.findById(claim).orElseThrow().status())
                .isEqualTo(NodeStatus.DISPUTED);

        nodeService.deleteNode(refuter);

        // после удаления refuter — рёбра от него каскадно ушли → claim видит только supports
        assertThat(nodeRepository.findById(claim).orElseThrow().status())
                .isEqualTo(NodeStatus.STANDING);
    }

    private UUID insertWithStatus(NodeStatus status) {
        UUID id = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.CLAIM.name(), "c", status.name(),
                userId, ru.basnukaev.argumentmap.repository.JdbcTimes.odt(now),
                ru.basnukaev.argumentmap.repository.JdbcTimes.odt(now)
        );
        return id;
    }

    private void insertEdge(UUID from, UUID to, String type) {
        jdbcTemplate.update(
                "INSERT INTO edges (id, from_node_id, to_node_id, edge_type, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), from, to, type, userId,
                ru.basnukaev.argumentmap.repository.JdbcTimes.odt(java.time.Instant.now())
        );
    }
}
