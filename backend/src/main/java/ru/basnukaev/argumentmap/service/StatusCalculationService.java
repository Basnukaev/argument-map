package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.EdgeType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.StatusAlgorithm;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * Пересчёт статусов узлов темы.
 *
 * Делегирует один из двух алгоритмов в зависимости от
 * {@code topic.statusAlgorithm} (ADR-044):
 * <ul>
 *   <li>{@link StatusAlgorithm#MVP} - existing fixpoint-итерация
 *       (учитывает SUPPORTS/REFUTES/INVALIDATES, см. ADR-007)
 *   <li>{@link StatusAlgorithm#DUNG_GROUNDED} - grounded labelling через
 *       {@link DungFrameworkService} (только attack-edges)
 * </ul>
 *
 * Метод НЕ помечен @Transactional — присоединяется к транзакции
 * вызывающего сервиса (EdgeService, NodeService).
 */
@Service
public class StatusCalculationService {

    private static final Logger log = LoggerFactory.getLogger(StatusCalculationService.class);

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final TopicRepository topicRepository;
    private final DungFrameworkService dungFrameworkService;

    public StatusCalculationService(NodeRepository nodeRepository,
                                    EdgeRepository edgeRepository,
                                    TopicRepository topicRepository,
                                    DungFrameworkService dungFrameworkService) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.topicRepository = topicRepository;
        this.dungFrameworkService = dungFrameworkService;
    }

    public void recalculateTopic(UUID topicId) {
        List<Node> nodes = nodeRepository.findByTopicId(topicId);
        if (nodes.isEmpty()) {
            return;
        }
        List<Edge> edges = edgeRepository.findByTopicId(topicId);
        Topic topic = topicRepository.findById(topicId).orElse(null);
        String algorithm = topic == null ? StatusAlgorithm.MVP : topic.statusAlgorithm();
        if (StatusAlgorithm.DUNG_GROUNDED.equals(algorithm)) {
            recalculateUsingDung(nodes, edges);
        } else {
            recalculateUsingMvp(nodes, edges);
        }
    }

    private void recalculateUsingMvp(List<Node> nodes, List<Edge> edges) {
        Map<UUID, List<Edge>> edgesByTo = edges.stream()
                .collect(Collectors.groupingBy(Edge::toNodeId));

        Map<UUID, NodeStatus> state = new HashMap<>(nodes.size());
        for (Node node : nodes) {
            state.put(node.id(), node.status());
        }

        int maxIterations = Math.max(20, nodes.size() * 2);
        int iter = 0;
        boolean changed = true;
        while (changed && iter < maxIterations) {
            changed = false;
            for (Node node : nodes) {
                List<Edge> incoming = edgesByTo.getOrDefault(node.id(), List.of());
                NodeStatus current = state.get(node.id());
                NodeStatus newStatus = computeStatus(current, incoming, state);
                if (newStatus != current) {
                    state.put(node.id(), newStatus);
                    changed = true;
                }
            }
            iter++;
        }
        if (iter == maxIterations && changed) {
            UUID topicId = nodes.get(0).topicId();
            log.warn("MVP пересчёт не сошёлся за {} итераций для темы {}", iter, topicId);
        }

        Instant now = Instant.now();
        for (Node node : nodes) {
            NodeStatus newStatus = state.get(node.id());
            if (newStatus != node.status()) {
                nodeRepository.updateStatus(node.id(), newStatus, now);
            }
        }
    }

    /**
     * Пересчёт через Dung's grounded labelling (ADR-044). Mapping:
     * IN → STANDING, OUT → REFUTED, UNDEC → DISPUTED. UNVERIFIED не
     * используется в Dung'е - каждый node получает определённый label
     */
    private void recalculateUsingDung(List<Node> nodes, List<Edge> edges) {
        Map<UUID, String> labels = dungFrameworkService.computeGroundedLabelling(nodes, edges);
        Instant now = Instant.now();
        for (Node node : nodes) {
            NodeStatus newStatus = mapLabelToStatus(labels.get(node.id()));
            if (newStatus != node.status()) {
                nodeRepository.updateStatus(node.id(), newStatus, now);
            }
        }
    }

    private static NodeStatus mapLabelToStatus(String label) {
        if (DungFrameworkService.IN.equals(label)) {
            return NodeStatus.STANDING;
        }
        if (DungFrameworkService.OUT.equals(label)) {
            return NodeStatus.REFUTED;
        }
        // UNDEC либо null - DISPUTED как наиболее близкий по семантике
        return NodeStatus.DISPUTED;
    }

    private NodeStatus computeStatus(NodeStatus current, List<Edge> incoming,
                                     Map<UUID, NodeStatus> state) {
        for (Edge e : incoming) {
            if (e.edgeType() == EdgeType.INVALIDATES
                    && state.get(e.fromNodeId()) == NodeStatus.STANDING) {
                return NodeStatus.REFUTED;
            }
        }

        boolean hasInfluencing = false;
        int standingSupports = 0;
        int standingRefutes = 0;
        for (Edge e : incoming) {
            if (e.edgeType() != EdgeType.SUPPORTS && e.edgeType() != EdgeType.REFUTES) {
                continue;
            }
            hasInfluencing = true;
            NodeStatus fromStatus = state.get(e.fromNodeId());
            if (fromStatus != NodeStatus.STANDING) {
                continue;
            }
            if (e.edgeType() == EdgeType.SUPPORTS) {
                standingSupports++;
            } else {
                standingRefutes++;
            }
        }
        // Узел без влияющих рёбер сохраняет текущий статус. Default UNVERIFIED
        // для свежесозданных узлов, пометка через будущий ручной механизм
        // (Этап 6+) переживёт пересчёты, пока на узел никто не ссылается.
        if (!hasInfluencing) {
            return current;
        }
        if (standingSupports > 0 && standingRefutes > 0) {
            return NodeStatus.DISPUTED;
        }
        if (standingSupports > 0) {
            return NodeStatus.STANDING;
        }
        if (standingRefutes > 0) {
            return NodeStatus.REFUTED;
        }
        return NodeStatus.UNVERIFIED;
    }
}
