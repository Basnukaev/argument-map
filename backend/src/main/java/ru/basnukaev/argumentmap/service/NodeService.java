package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Revision;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.exception.NodeIsRootException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.RevisionRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

@Service
public class NodeService {

    private final NodeRepository nodeRepository;
    private final TopicRepository topicRepository;
    private final RevisionRepository revisionRepository;
    private final StatusCalculationService statusCalculationService;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public NodeService(NodeRepository nodeRepository,
                       TopicRepository topicRepository,
                       RevisionRepository revisionRepository,
                       StatusCalculationService statusCalculationService,
                       PermissionService permissionService,
                       AuditLogService auditLogService) {
        this.nodeRepository = nodeRepository;
        this.topicRepository = topicRepository;
        this.revisionRepository = revisionRepository;
        this.statusCalculationService = statusCalculationService;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content, UUID userId) {
        // legacy перегрузка - без permission check (для тестов и
        // batch importers). REST endpoint должен использовать перегрузку
        // с role.
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
        Instant now = Instant.now();
        Node node = new Node(
                UUID.randomUUID(), topicId, type, content,
                NodeStatus.UNVERIFIED, null, null, 0,
                userId, now, now
        );
        nodeRepository.save(node);

        // ADR-043 Amendment 3 (22.d) - audit CREATE с topicId как parent
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeType", type.name());
        snapshot.put("content", content);
        auditLogService.logCreate(AuditEntityType.NODE, node.id(),
                AuditEntityType.TOPIC, topicId, userId, snapshot);

        return node;
    }

    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content,
                           UUID userId, String role) {
        // ADR-043: write требует canWriteTopic
        permissionService.assertCanWrite(topicId, userId, role);
        return createNode(topicId, type, content, userId);
    }

    /**
     * Обновление координат узла на канвасе. Не пишет revision, не меняет
     * updatedAt, не триггерит пересчёт статусов. Бросает NodeNotFoundException
     * если узла нет.
     */
    @Transactional
    public Node updatePosition(UUID nodeId, Double posX, Double posY) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        nodeRepository.updatePosition(nodeId, posX, posY);
        return new Node(
                existing.id(), existing.topicId(), existing.nodeType(),
                existing.content(), existing.status(),
                posX, posY, existing.zIndex(),
                existing.createdBy(), existing.createdAt(), existing.updatedAt()
        );
    }

    @Transactional
    public Node updatePosition(UUID nodeId, Double posX, Double posY,
                               UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);
        return updatePosition(nodeId, posX, posY);
    }

    /**
     * Обновляет содержимое узла и пишет revision (before/after).
     * Не триггерит пересчёт статусов: content не входит в алгоритм.
     */
    @Transactional
    public Node updateContent(UUID nodeId, String newContent, UUID userId) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        Instant now = Instant.now();
        Revision revision = new Revision(
                UUID.randomUUID(), nodeId,
                existing.content(), newContent,
                userId, now
        );
        revisionRepository.save(revision);

        Node updated = new Node(
                existing.id(), existing.topicId(), existing.nodeType(),
                newContent, existing.status(),
                existing.posX(), existing.posY(), existing.zIndex(),
                existing.createdBy(), existing.createdAt(), now
        );
        nodeRepository.update(updated);

        // ADR-043 Amendment 3 (22.d) - audit content UPDATE
        Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
        diff.put("content", new AuditLogService.FieldDiff(
                existing.content(), newContent));
        auditLogService.logUpdate(AuditEntityType.NODE, nodeId,
                AuditEntityType.TOPIC, existing.topicId(), userId, diff);

        return updated;
    }

    @Transactional
    public Node updateContent(UUID nodeId, String newContent, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);
        return updateContent(nodeId, newContent, userId);
    }

    @Transactional
    public void deleteNode(UUID nodeId) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        // защита от удаления корневого вопроса темы - разрушит граф
        // (orphan edges + status calculation сломан). Чтобы убрать
        // корень - удалять тему целиком
        Topic topic = topicRepository.findById(existing.topicId())
                .orElseThrow(() -> new TopicNotFoundException(existing.topicId()));
        if (nodeId.equals(topic.rootNodeId())) {
            throw new NodeIsRootException(nodeId, topic.id());
        }
        nodeRepository.deleteById(nodeId);
        statusCalculationService.recalculateTopic(existing.topicId());
    }

    @Transactional
    public void deleteNode(UUID nodeId, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);

        // ADR-043 Amendment 3 (22.d) - audit DELETE до самого delete
        // (после deleteNode existing уже не достаём из БД)
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeType", existing.nodeType().name());
        snapshot.put("content", existing.content());
        auditLogService.logDelete(AuditEntityType.NODE, nodeId,
                AuditEntityType.TOPIC, existing.topicId(), userId, snapshot);

        deleteNode(nodeId);
    }

    /**
     * Stacking order: ставит узел на передний план относительно других
     * узлов темы. z_index = max(z_index темы) + 1. Если узел уже сверху -
     * всё равно поднимает, чтобы операция была идемпотентной семантически
     * (повторный вызов = всё ещё сверху). Не пишет revision, не меняет
     * updatedAt (см. NodeRepository.updateZIndex).
     */
    @Transactional
    public Node bringToFront(UUID nodeId, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);

        int newZ = nodeRepository.findMaxZIndex(existing.topicId()) + 1;
        nodeRepository.updateZIndex(nodeId, newZ);
        return new Node(
                existing.id(), existing.topicId(), existing.nodeType(),
                existing.content(), existing.status(),
                existing.posX(), existing.posY(), newZ,
                existing.createdBy(), existing.createdAt(), existing.updatedAt()
        );
    }

    /**
     * Stacking order: ставит узел на задний план. z_index = min(z_index
     * темы) - 1. См. bringToFront для деталей семантики.
     */
    @Transactional
    public Node sendToBack(UUID nodeId, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);

        int newZ = nodeRepository.findMinZIndex(existing.topicId()) - 1;
        nodeRepository.updateZIndex(nodeId, newZ);
        return new Node(
                existing.id(), existing.topicId(), existing.nodeType(),
                existing.content(), existing.status(),
                existing.posX(), existing.posY(), newZ,
                existing.createdBy(), existing.createdAt(), existing.updatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<Revision> getRevisions(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return revisionRepository.findByNodeId(nodeId);
    }

    @Transactional(readOnly = true)
    public List<Revision> getRevisions(UUID nodeId, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanRead(existing.topicId(), userId, role);
        return revisionRepository.findByNodeId(nodeId);
    }
}
