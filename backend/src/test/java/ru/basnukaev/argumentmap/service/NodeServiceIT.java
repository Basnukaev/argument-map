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
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.AuditAction;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Revision;
import ru.basnukaev.argumentmap.exception.NodeIsRootException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.AuditLogRepository;
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
    private AuditLogRepository auditLogRepository;

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

    // ---- bulkDeleteNodes (backlog «Bulk audit log consolidation», 2026-05-19) ----

    @Test
    void bulkDeleteNodes_writesOneAuditRow_notNRows() {
        // создаём 3 узла, удаляем bulk'ом → ровно 1 BULK_DELETE row,
        // не 3 отдельных DELETE
        Node n1 = nodeService.createNode(topicId, NodeType.CLAIM, "тезис 1", userId);
        Node n2 = nodeService.createNode(topicId, NodeType.CLAIM, "тезис 2", userId);
        Node n3 = nodeService.createNode(topicId, NodeType.EVIDENCE, "довод", userId);

        long bulkBefore = auditLogRepository.countByEntity(AuditEntityType.TOPIC, topicId);

        NodeService.BulkDeleteResult result = nodeService.bulkDeleteNodes(
                List.of(n1.id(), n2.id(), n3.id()), userId, UserRole.USER
        );

        assertThat(result.deletedIds()).containsExactlyInAnyOrder(n1.id(), n2.id(), n3.id());
        assertThat(result.skippedRootIds()).isEmpty();
        assertThat(nodeRepository.findById(n1.id())).isEmpty();
        assertThat(nodeRepository.findById(n2.id())).isEmpty();
        assertThat(nodeRepository.findById(n3.id())).isEmpty();

        // ровно один новый audit row на topic entity_id - BULK_DELETE
        long bulkAfter = auditLogRepository.countByEntity(AuditEntityType.TOPIC, topicId);
        assertThat(bulkAfter - bulkBefore).isEqualTo(1);

        // и это именно BULK_DELETE action с count=3 в changes
        var rows = auditLogRepository.findByEntityPage(AuditEntityType.TOPIC, topicId, 5, 0);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).action()).isEqualTo(AuditAction.BULK_DELETE);
        // jsonb нормализует JSON (пробел после `:`), используем substring-чек
        // вместо contains("\"count\":3")
        assertThat(rows.get(0).changes()).contains("\"count\"");
        assertThat(rows.get(0).changes()).contains("3");
        assertThat(rows.get(0).changes()).contains("\"childEntityType\"");
        assertThat(rows.get(0).changes()).contains("\"NODE\"");
        assertThat(rows.get(0).changes()).contains(n1.id().toString());
        assertThat(rows.get(0).changes()).contains(n2.id().toString());
        assertThat(rows.get(0).changes()).contains(n3.id().toString());
    }

    @Test
    void bulkDeleteNodes_filtersRootNode_returnsSkipped() {
        // корневой узел не удаляется (NodeIsRootException на single-delete),
        // но не fail'ит весь bulk - возвращается в skippedRootIds
        Node root = nodeService.createNode(topicId, NodeType.QUESTION, "корень?", userId);
        topicRepository.updateRootNodeId(topicId, root.id());
        Node child1 = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);
        Node child2 = nodeService.createNode(topicId, NodeType.CLAIM, "тезис 2", userId);

        NodeService.BulkDeleteResult result = nodeService.bulkDeleteNodes(
                List.of(root.id(), child1.id(), child2.id()), userId, UserRole.USER
        );

        assertThat(result.deletedIds()).containsExactlyInAnyOrder(child1.id(), child2.id());
        assertThat(result.skippedRootIds()).containsExactly(root.id());
        assertThat(nodeRepository.findById(root.id())).isPresent();
        assertThat(nodeRepository.findById(child1.id())).isEmpty();
    }

    @Test
    void bulkDeleteNodes_nonWriter_throwsAccessDenied() {
        // другой user (не owner темы) не может удалять - assertCanWrite бросает
        Node n = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);

        UUID strangerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                strangerId, "stranger-" + strangerId, strangerId + "@x.com"
        );

        assertThatThrownBy(() -> nodeService.bulkDeleteNodes(
                List.of(n.id()), strangerId, UserRole.USER
        )).isInstanceOf(TopicAccessDeniedException.class);

        // узел остался - rollback
        assertThat(nodeRepository.findById(n.id())).isPresent();
    }

    @Test
    void bulkDeleteNodes_acrossDifferentTopics_throwsIllegalArgument() {
        // bulk должен быть в пределах одной темы (один permission check,
        // один parent для audit row)
        UUID otherTopicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                otherTopicId, "T2", userId
        );
        Node n1 = nodeService.createNode(topicId, NodeType.CLAIM, "из 1", userId);
        Node n2 = nodeService.createNode(otherTopicId, NodeType.CLAIM, "из 2", userId);

        assertThatThrownBy(() -> nodeService.bulkDeleteNodes(
                List.of(n1.id(), n2.id()), userId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("одной теме");
    }

    @Test
    void bulkDeleteNodes_nonExistentNode_throwsNotFound() {
        // несуществующий id → весь bulk fail'ит (consistency)
        Node n = nodeService.createNode(topicId, NodeType.CLAIM, "тезис", userId);
        UUID ghost = UUID.randomUUID();

        assertThatThrownBy(() -> nodeService.bulkDeleteNodes(
                List.of(n.id(), ghost), userId, UserRole.USER
        )).isInstanceOf(NodeNotFoundException.class);

        // первый узел тоже не удалён (rollback)
        assertThat(nodeRepository.findById(n.id())).isPresent();
    }

    @Test
    void bulkDeleteNodes_emptyList_throwsIllegalArgument() {
        assertThatThrownBy(() -> nodeService.bulkDeleteNodes(
                List.of(), userId, UserRole.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Z-index overflow guard tests ----

    @Test
    void bringToFront_atMaxZIndex_throwsIllegalState() {
        // узел с z_index = Integer.MAX_VALUE → bringToFront должен бросить
        // IllegalStateException (overflow guard), а не молча переполнить int
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "переполнение", userId);
        jdbcTemplate.update("UPDATE nodes SET z_index = ? WHERE id = ?",
                Integer.MAX_VALUE, node.id());

        assertThatThrownBy(() -> nodeService.bringToFront(node.id(), userId, "USER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overflow");
    }

    @Test
    void sendToBack_atMinZIndex_throwsIllegalState() {
        Node node = nodeService.createNode(topicId, NodeType.CLAIM, "underflow", userId);
        jdbcTemplate.update("UPDATE nodes SET z_index = ? WHERE id = ?",
                Integer.MIN_VALUE, node.id());

        assertThatThrownBy(() -> nodeService.sendToBack(node.id(), userId, "USER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("underflow");
    }

    @Test
    void bulkDeleteNodes_onlyRootInRequest_noAuditRow() {
        // если в запросе только корневой узел - удалять нечего, audit
        // row писать тоже не нужно (no-op)
        Node root = nodeService.createNode(topicId, NodeType.QUESTION, "корень?", userId);
        topicRepository.updateRootNodeId(topicId, root.id());

        long before = auditLogRepository.countByEntity(AuditEntityType.TOPIC, topicId);

        NodeService.BulkDeleteResult result = nodeService.bulkDeleteNodes(
                List.of(root.id()), userId, UserRole.USER
        );

        assertThat(result.deletedIds()).isEmpty();
        assertThat(result.skippedRootIds()).containsExactly(root.id());

        // ни одного нового audit row - операция no-op
        long after = auditLogRepository.countByEntity(AuditEntityType.TOPIC, topicId);
        assertThat(after).isEqualTo(before);
    }
}
