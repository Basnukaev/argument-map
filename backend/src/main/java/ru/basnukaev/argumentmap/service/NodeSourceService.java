package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeSource;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.exception.SourceNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository;
import ru.basnukaev.argumentmap.repository.NodeSourceRepository.NodeSourceWithLocation;
import ru.basnukaev.argumentmap.repository.SourceRepository;

@Service
public class NodeSourceService {

    private final NodeSourceRepository nodeSourceRepository;
    private final NodeRepository nodeRepository;
    private final SourceRepository sourceRepository;
    private final PermissionService permissionService;

    public NodeSourceService(NodeSourceRepository nodeSourceRepository,
                             NodeRepository nodeRepository,
                             SourceRepository sourceRepository,
                             PermissionService permissionService) {
        this.nodeSourceRepository = nodeSourceRepository;
        this.nodeRepository = nodeRepository;
        this.sourceRepository = sourceRepository;
        this.permissionService = permissionService;
    }

    /**
     * Attach с write-guard (ADR-043). Citation - контентное изменение узла,
     * поэтому требует write-доступа к теме узла. Резолвит topicId узла и
     * ассертит assertCanWrite. Без этого любой authenticated мог вешать
     * citation на узлы чужих PRIVATE/SHARED тем.
     *
     * @throws NodeNotFoundException если узла нет (404)
     * @throws ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException
     *         если нет write-доступа к теме узла (403)
     */
    @Transactional
    public NodeSource attachSource(UUID nodeId, UUID sourceId,
                                   String quote, String context, String location,
                                   UUID actorUserId, String actorRole) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(node.topicId(), actorUserId, actorRole);
        return attachSource(nodeId, sourceId, quote, context, location);
    }

    /**
     * Legacy overload без permission-check. Internal callers + IT.
     * REST endpoint должен звать
     * {@link #attachSource(UUID, UUID, String, String, String, UUID, String)}.
     */
    @Transactional
    public NodeSource attachSource(UUID nodeId, UUID sourceId,
                                   String quote, String context, String location) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        if (sourceRepository.findById(sourceId).isEmpty()) {
            throw new SourceNotFoundException(sourceId);
        }
        NodeSource link = NodeSource.legacyMode(nodeId, sourceId, quote, context, location, Instant.now());
        nodeSourceRepository.save(link);
        return link;
    }

    @Transactional(readOnly = true)
    public List<NodeSource> getNodeSources(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return nodeSourceRepository.findByNodeId(nodeId);
    }

    /**
     * Расширенная версия для GET endpoint - возвращает rows с structured
     * citation через 9 LEFT JOIN. Используется во всех clients
     */
    @Transactional(readOnly = true)
    public List<NodeSourceWithLocation> getNodeSourcesWithLocation(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return nodeSourceRepository.findByNodeIdWithLocation(nodeId);
    }

    /**
     * List с read-guard (ADR-043). Резолвит topicId узла и ассертит
     * assertCanRead - GET /nodes/{id}/sources раньше отдавал citations
     * узлов приватных тем любому. Симметрично attach write-guard'у.
     *
     * @throws NodeNotFoundException если узла нет (404)
     * @throws ru.basnukaev.argumentmap.exception.TopicAccessDeniedException
     *         если нет read-доступа к теме узла (403)
     */
    @Transactional(readOnly = true)
    public List<NodeSourceWithLocation> getNodeSourcesWithLocation(UUID nodeId,
                                                                   UUID actorUserId, String actorRole) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanRead(node.topicId(), actorUserId, actorRole);
        return nodeSourceRepository.findByNodeIdWithLocation(nodeId);
    }

    /**
     * Detach по surrogate id, scoped к узлу (миграция 25, ADR-FK-A).
     * Удаляет citation только если она принадлежит {@code nodeId} из
     * path - защита от IDOR: DELETE /nodes/{nodeId}/sources/{id} не
     * должен удалять citation другого узла по голому id. Если citation
     * не существует ИЛИ принадлежит другому узлу → 404 (не leak'аем
     * существование чужой citation).
     */
    @Transactional
    public void detachById(UUID nodeId, UUID nodeSourceId) {
        boolean removed = nodeSourceRepository.deleteByIdAndNode(nodeSourceId, nodeId);
        if (!removed) {
            throw new SourceNotFoundException(nodeSourceId);
        }
    }

    /**
     * Detach с write-guard (ADR-043) поверх node-scoped delete. Резолвит
     * topicId узла и ассертит assertCanWrite - удаление citation это
     * контентное изменение узла. Node-scoped delete (WHERE id=? AND
     * node_id=?) остаётся как IDOR-защита. Без этого любой authenticated
     * мог снимать citation с узлов чужих тем.
     *
     * @throws NodeNotFoundException если узла нет (404)
     * @throws SourceNotFoundException если citation не существует ИЛИ
     *         принадлежит другому узлу (404)
     * @throws ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException
     *         если нет write-доступа к теме узла (403)
     */
    @Transactional
    public void detachById(UUID nodeId, UUID nodeSourceId, UUID actorUserId, String actorRole) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(node.topicId(), actorUserId, actorRole);
        detachById(nodeId, nodeSourceId);
    }
}
