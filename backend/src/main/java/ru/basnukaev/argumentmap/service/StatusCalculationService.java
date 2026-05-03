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
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;

/**
 * Пересчёт статусов узлов темы.
 *
 * Алгоритм — фикспоинт-итерация в памяти. Каждая итерация перебирает все
 * узлы и пересчитывает статус по входящим рёбрам и текущему состоянию
 * соседей. Итерации продолжаются до сходимости или достижения лимита.
 * В БД обновляются только узлы с изменившимся статусом.
 *
 * INVALIDATES от STANDING-источника — жёсткий kill: цель → REFUTED
 * безусловно. QUALIFIES и RESPONDS_TO в алгоритм не входят (см. ADR-007).
 *
 * Метод НЕ помечен @Transactional — присоединяется к транзакции
 * вызывающего сервиса (EdgeService, NodeService).
 */
@Service
public class StatusCalculationService {

    private static final Logger log = LoggerFactory.getLogger(StatusCalculationService.class);

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    public StatusCalculationService(NodeRepository nodeRepository, EdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    public void recalculateTopic(UUID topicId) {
        List<Node> nodes = nodeRepository.findByTopicId(topicId);
        if (nodes.isEmpty()) {
            return;
        }
        List<Edge> edges = edgeRepository.findByTopicId(topicId);
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
            log.warn("Пересчёт не сошёлся за {} итераций для темы {}", iter, topicId);
        }

        Instant now = Instant.now();
        for (Node node : nodes) {
            NodeStatus newStatus = state.get(node.id());
            if (newStatus != node.status()) {
                nodeRepository.updateStatus(node.id(), newStatus, now);
            }
        }
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
