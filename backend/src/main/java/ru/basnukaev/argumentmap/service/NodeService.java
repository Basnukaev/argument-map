package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.Revision;
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

    public NodeService(NodeRepository nodeRepository,
                       TopicRepository topicRepository,
                       RevisionRepository revisionRepository,
                       StatusCalculationService statusCalculationService) {
        this.nodeRepository = nodeRepository;
        this.topicRepository = topicRepository;
        this.revisionRepository = revisionRepository;
        this.statusCalculationService = statusCalculationService;
    }

    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content, UUID userId) {
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
        Instant now = Instant.now();
        Node node = new Node(
                UUID.randomUUID(), topicId, type, content,
                NodeStatus.UNVERIFIED, userId, now, now
        );
        nodeRepository.save(node);
        return node;
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
                existing.createdBy(), existing.createdAt(), now
        );
        nodeRepository.update(updated);
        return updated;
    }

    @Transactional
    public void deleteNode(UUID nodeId) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        nodeRepository.deleteById(nodeId);
        statusCalculationService.recalculateTopic(existing.topicId());
    }

    @Transactional(readOnly = true)
    public List<Revision> getRevisions(UUID nodeId) {
        if (nodeRepository.findById(nodeId).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return revisionRepository.findByNodeId(nodeId);
    }
}
