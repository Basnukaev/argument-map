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
    private final PermissionService permissionService;

    public EdgeService(EdgeRepository edgeRepository, NodeRepository nodeRepository,
                       StatusCalculationService statusCalculationService,
                       PermissionService permissionService) {
        this.edgeRepository = edgeRepository;
        this.nodeRepository = nodeRepository;
        this.statusCalculationService = statusCalculationService;
        this.permissionService = permissionService;
    }

    /**
     * Перегрузка без sourceHandle/targetHandle - для случаев когда сторона
     * подключения не важна (например, ребро создано не через UI drag,
     * а через bulk-импорт или тестовую фикстуру). Эквивалентно вызову
     * с null/null
     */
    @Transactional
    public Edge createEdge(UUID fromNodeId, UUID toNodeId, EdgeType type,
                           String rationale, UUID userId) {
        return createEdge(fromNodeId, toNodeId, type, rationale, null, null, userId);
    }

    @Transactional
    public Edge createEdge(UUID fromNodeId, UUID toNodeId, EdgeType type,
                           String rationale, String sourceHandle, String targetHandle,
                           UUID userId) {
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
                rationale, sourceHandle, targetHandle, userId, Instant.now()
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

    /**
     * ADR-043: write требует canWriteTopic для темы к которой принадлежит ребро.
     */
    @Transactional
    public Edge createEdge(UUID fromNodeId, UUID toNodeId, EdgeType type,
                           String rationale, String sourceHandle, String targetHandle,
                           UUID userId, String role) {
        // Проверяем существование from-node чтобы достать topic_id
        Node from = nodeRepository.findById(fromNodeId)
                .orElseThrow(() -> new NodeNotFoundException(fromNodeId));
        permissionService.assertCanWrite(from.topicId(), userId, role);
        return createEdge(fromNodeId, toNodeId, type, rationale,
                sourceHandle, targetHandle, userId);
    }

    @Transactional
    public void deleteEdge(UUID edgeId, UUID userId, String role) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
        UUID topicId = nodeRepository.findById(existing.fromNodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.fromNodeId()))
                .topicId();
        permissionService.assertCanWrite(topicId, userId, role);
        deleteEdge(edgeId);
    }

    @Transactional
    public Edge updateEdge(UUID edgeId, UUID fromNodeId, UUID toNodeId, EdgeType edgeType,
                           String rationale, String sourceHandle, String targetHandle,
                           UUID userId, String role) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
        UUID topicId = nodeRepository.findById(existing.fromNodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.fromNodeId()))
                .topicId();
        permissionService.assertCanWrite(topicId, userId, role);
        return updateEdge(edgeId, fromNodeId, toNodeId, edgeType,
                rationale, sourceHandle, targetHandle);
    }

    /**
     * Partial update ребра. Все поля null в аргументах сохраняют текущее
     * значение, не-null - применяются. Финальное состояние валидируется
     * целиком (selfloop, граница темы, матрица ADR-010). При нарушении
     * валидации выбрасывается InvalidEdgeException и в БД ничего не
     * меняется - @Transactional откатит транзакцию.
     *
     * Ограничение: через этот метод нельзя "очистить" rationale или
     * handle'ы (выставить в null). Для MVP не нужно - reconnect всегда
     * передаёт конкретные значения. Если потребуется - отдельный feature
     * с явным sentinel или JsonNullable.
     *
     * Если изменился fromNode/toNode/edgeType - пересчитываем статусы темы.
     * Если только rationale/handle'ы - пересчёт не нужен (на алгоритм не влияют).
     */
    @Transactional
    public Edge updateEdge(UUID edgeId, UUID fromNodeId, UUID toNodeId, EdgeType edgeType,
                           String rationale, String sourceHandle, String targetHandle) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));

        UUID newFromId = fromNodeId != null ? fromNodeId : existing.fromNodeId();
        UUID newToId = toNodeId != null ? toNodeId : existing.toNodeId();
        EdgeType newType = edgeType != null ? edgeType : existing.edgeType();
        String newRationale = rationale != null ? rationale : existing.rationale();
        String newSourceHandle = sourceHandle != null ? sourceHandle : existing.sourceHandle();
        String newTargetHandle = targetHandle != null ? targetHandle : existing.targetHandle();

        if (newFromId.equals(newToId)) {
            throw new InvalidEdgeException("узел не может ссылаться на себя");
        }
        Node from = nodeRepository.findById(newFromId)
                .orElseThrow(() -> new NodeNotFoundException(newFromId));
        Node to = nodeRepository.findById(newToId)
                .orElseThrow(() -> new NodeNotFoundException(newToId));
        if (!from.topicId().equals(to.topicId())) {
            throw new InvalidEdgeException("ребро не может пересекать границу темы");
        }
        if (!EdgeSemantics.isAllowed(from.nodeType(), newType, to.nodeType())) {
            throw new InvalidEdgeException(
                    "тип связи %s недопустим для пары (%s -> %s)".formatted(
                            newType, from.nodeType(), to.nodeType()));
        }

        Edge updated = new Edge(
                existing.id(), newFromId, newToId, newType,
                newRationale, newSourceHandle, newTargetHandle,
                existing.createdBy(), existing.createdAt()
        );
        edgeRepository.update(updated);

        boolean structuralChange = !existing.fromNodeId().equals(newFromId)
                || !existing.toNodeId().equals(newToId)
                || existing.edgeType() != newType;
        if (structuralChange) {
            statusCalculationService.recalculateTopic(from.topicId());
        }
        return updated;
    }
}
