package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.exception.EdgeNotFoundException;
import ru.basnukaev.argumentmap.exception.InvalidEdgeException;
import ru.basnukaev.argumentmap.exception.NodeNotFoundException;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;

@Service
public class EdgeService {

    private final EdgeRepository edgeRepository;
    private final NodeRepository nodeRepository;
    private final StatusCalculationService statusCalculationService;

    public EdgeService(EdgeRepository edgeRepository, NodeRepository nodeRepository,
                       StatusCalculationService statusCalculationService) {
        this.edgeRepository = edgeRepository;
        this.nodeRepository = nodeRepository;
        this.statusCalculationService = statusCalculationService;
    }

    @Transactional
    public Edge createEdge(UUID fromNodeId, UUID toNodeId, EdgeType type,
                           String rationale, UUID userId) {
        if (fromNodeId.equals(toNodeId)) {
            throw new InvalidEdgeException("узел не может ссылаться на себя");
        }
        Node from = nodeRepository.findById(fromNodeId)
                .orElseThrow(() -> new NodeNotFoundException(fromNodeId));
        Node to = nodeRepository.findById(toNodeId)
                .orElseThrow(() -> new NodeNotFoundException(toNodeId));
        if (!from.topicId().equals(to.topicId())) {
            throw new InvalidEdgeException("ребро не может пересекать границу темы");
        }
        if (!EdgeSemantics.isAllowed(from.nodeType(), type, to.nodeType())) {
            throw new InvalidEdgeException(
                    "тип связи %s недопустим для пары (%s -> %s)".formatted(
                            type, from.nodeType(), to.nodeType()));
        }

        Edge edge = new Edge(
                UUID.randomUUID(), fromNodeId, toNodeId, type,
                rationale, userId, Instant.now()
        );
        edgeRepository.save(edge);
        statusCalculationService.recalculateTopic(from.topicId());
        return edge;
    }

    @Transactional
    public void deleteEdge(UUID edgeId) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
        UUID topicId = nodeRepository.findById(existing.fromNodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.fromNodeId()))
                .topicId();
        edgeRepository.deleteById(edgeId);
        statusCalculationService.recalculateTopic(topicId);
    }
}
