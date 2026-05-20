package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.domain.AuditEntityType;
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
    private final AuditLogService auditLogService;

    public EdgeService(EdgeRepository edgeRepository, NodeRepository nodeRepository,
                       StatusCalculationService statusCalculationService,
                       PermissionService permissionService,
                       AuditLogService auditLogService) {
        this.edgeRepository = edgeRepository;
        this.nodeRepository = nodeRepository;
        this.statusCalculationService = statusCalculationService;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
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
                rationale, sourceHandle, targetHandle, userId, Instant.now(), 0
        );
        edgeRepository.save(edge);
        statusCalculationService.recalculateTopic(from.topicId());

        // ADR-043 Amendment 3 (22.d) - audit CREATE
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("fromNodeId", fromNodeId.toString())
                .put("toNodeId", toNodeId.toString())
                .put("edgeType", type.name())
                .put("rationale", rationale)
                .build();
        auditLogService.logCreate(AuditEntityType.EDGE, edge.id(),
                AuditEntityType.TOPIC, from.topicId(), userId, snapshot);

        return edge;
    }

    @Transactional
    public void deleteEdge(UUID edgeId) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
        UUID topicId = nodeRepository.findById(existing.fromNodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.fromNodeId()))
                .topicId();
        doDeleteEdge(existing, topicId);
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

        // ADR-043 Amendment 3 (22.d) - audit DELETE до удаления (после
        // deleteEdge мы потеряем existing)
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("fromNodeId", existing.fromNodeId().toString())
                .put("toNodeId", existing.toNodeId().toString())
                .put("edgeType", existing.edgeType().name())
                .put("rationale", existing.rationale())
                .build();
        auditLogService.logDelete(AuditEntityType.EDGE, edgeId,
                AuditEntityType.TOPIC, topicId, userId, snapshot);

        // уже имеем загруженный edge и topicId — передаём в helper,
        // чтобы не грузить их повторно внутри deleteEdge(UUID)
        doDeleteEdge(existing, topicId);
    }

    /**
     * Внутренний helper: удалить ребро + пересчитать статусы темы.
     * Принимает уже загруженные данные — исключает повторный запрос в БД
     * когда caller уже знает edge и topicId (fix double-load в deleteEdge pair).
     */
    private void doDeleteEdge(Edge edge, UUID topicId) {
        edgeRepository.deleteById(edge.id());
        statusCalculationService.recalculateTopic(topicId);
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
        Edge updated = updateEdge(edgeId, fromNodeId, toNodeId, edgeType,
                rationale, sourceHandle, targetHandle);

        // ADR-043 Amendment 3 (22.d) - audit UPDATE с per-field diff.
        // Здесь т.к. знаем actor (userId из request); legacy updateEdge
        // без role вызывается только из тестов - не пишет audit.
        // UUID/enum поля сравниваем как строки для consistency со
        // snapshot blob - JSON-сериализация UUID в jsonb даёт string
        Map<String, AuditLogService.FieldDiff> diff = AuditLogService.diff()
                .compare("fromNodeId",
                        existing.fromNodeId().toString(),
                        updated.fromNodeId().toString())
                .compare("toNodeId",
                        existing.toNodeId().toString(),
                        updated.toNodeId().toString())
                .compare("edgeType",
                        existing.edgeType().name(),
                        updated.edgeType().name())
                .compare("rationale", existing.rationale(), updated.rationale())
                .build();
        if (!diff.isEmpty()) {
            auditLogService.logUpdate(AuditEntityType.EDGE, edgeId,
                    AuditEntityType.TOPIC, topicId, userId, diff);
        }
        return updated;
    }

    /**
     * Stacking order: ставит ребро на передний план относительно других
     * рёбер темы. z_index = max(z_index рёбер темы) + 1. Тема определяется
     * через from-узел ребра (инвариант EdgeService). Не пишет revision, не
     * меняет updatedAt (z-order - UI affordance, не доменное изменение).
     * Mirror NodeService.bringToFront.
     */
    @Transactional
    public Edge bringToFront(UUID edgeId, UUID userId, String role) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
        UUID topicId = nodeRepository.findById(existing.fromNodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.fromNodeId()))
                .topicId();
        permissionService.assertCanWrite(topicId, userId, role);

        int newZ = edgeRepository.findMaxZIndex(topicId) + 1;
        edgeRepository.updateZIndex(edgeId, newZ);
        return new Edge(
                existing.id(), existing.fromNodeId(), existing.toNodeId(),
                existing.edgeType(), existing.rationale(),
                existing.sourceHandle(), existing.targetHandle(),
                existing.createdBy(), existing.createdAt(), newZ
        );
    }

    /**
     * Stacking order: ставит ребро на задний план. z_index = min(z_index
     * рёбер темы) - 1. Mirror bringToFront / NodeService.sendToBack.
     */
    @Transactional
    public Edge sendToBack(UUID edgeId, UUID userId, String role) {
        Edge existing = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new EdgeNotFoundException(edgeId));
        UUID topicId = nodeRepository.findById(existing.fromNodeId())
                .orElseThrow(() -> new NodeNotFoundException(existing.fromNodeId()))
                .topicId();
        permissionService.assertCanWrite(topicId, userId, role);

        int newZ = edgeRepository.findMinZIndex(topicId) - 1;
        edgeRepository.updateZIndex(edgeId, newZ);
        return new Edge(
                existing.id(), existing.fromNodeId(), existing.toNodeId(),
                existing.edgeType(), existing.rationale(),
                existing.sourceHandle(), existing.targetHandle(),
                existing.createdBy(), existing.createdAt(), newZ
        );
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
                existing.createdBy(), existing.createdAt(), existing.zIndex()
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
