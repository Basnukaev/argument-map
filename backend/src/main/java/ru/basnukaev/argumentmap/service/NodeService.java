package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // Whitelist для originalLang (миграция 44). Должны совпадать с CHECK
    // constraint в БД. translation_lang валидируется в NodeTranslationService
    // т.к. translation теперь в child-таблице
    private static final Set<String> ALLOWED_ORIGINAL_LANGS = Set.of("ar", "ru", "en");

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
        return createNode(topicId, type, content, null, userId);
    }

    /**
     * Создание узла с опциональным originalLang. originalLang ∈ {ar, ru, en}
     * либо null (frontend auto-detect через hasArabicScript). Переводы
     * добавляются отдельно через {@code NodeTranslationService.addTranslation}.
     *
     * <p>Legacy перегрузка без originalLang идёт сюда с null - backward-compat
     * для существующих callers (TopicImportService и т.д.).
     */
    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content,
                           String originalLang, UUID userId) {
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
        validateOriginalLang(originalLang);

        Instant now = Instant.now();
        Node node = new Node(
                UUID.randomUUID(), topicId, type, content,
                NodeStatus.UNVERIFIED, null, null, 0,
                userId, now, now,
                originalLang
        );
        nodeRepository.save(node);

        // ADR-043 Amendment 3 (22.d) - audit CREATE с topicId как parent
        AuditLogService.SnapshotBuilder builder = AuditLogService.snapshot()
                .put("nodeType", type.name())
                .put("content", content);
        if (originalLang != null) {
            builder.put("originalLang", originalLang);
        }
        auditLogService.logCreate(AuditEntityType.NODE, node.id(),
                AuditEntityType.TOPIC, topicId, userId, builder.build());

        return node;
    }

    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content,
                           UUID userId, String role) {
        // ADR-043: write требует canWriteTopic
        permissionService.assertCanWrite(topicId, userId, role);
        return createNode(topicId, type, content, userId);
    }

    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content,
                           String originalLang, UUID userId, String role) {
        permissionService.assertCanWrite(topicId, userId, role);
        return createNode(topicId, type, content, originalLang, userId);
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
                existing.createdBy(), existing.createdAt(), existing.updatedAt(),
                existing.originalLang()
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
        return updateContent(nodeId, newContent, NoChange.INSTANCE, userId);
    }

    /**
     * Расширенный update - помимо content может опционально обновить
     * originalLang. Если параметр - {@link NoChange#INSTANCE}, поле в БД
     * остаётся прежним. Если параметр явно null (через REST DTO с явным
     * null или пустой строкой) - поле очищается.
     *
     * <p>Revision записывается только для изменения content. originalLang -
     * metadata, не version-controlled.
     */
    @Transactional
    public Node updateContent(UUID nodeId, Object newContentBox,
                              Object newOriginalLang, UUID userId) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        String resolvedContent = (newContentBox instanceof NoChange)
                ? existing.content()
                : (String) newContentBox;
        String resolvedOriginalLang = (newOriginalLang instanceof NoChange)
                ? existing.originalLang()
                : (String) newOriginalLang;

        validateOriginalLang(resolvedOriginalLang);

        Instant now = Instant.now();
        boolean contentChanged = !Objects.equals(existing.content(), resolvedContent);
        if (contentChanged) {
            Revision revision = new Revision(
                    UUID.randomUUID(), nodeId,
                    existing.content(), resolvedContent,
                    userId, now
            );
            revisionRepository.save(revision);
        }

        Node updated = new Node(
                existing.id(), existing.topicId(), existing.nodeType(),
                resolvedContent, existing.status(),
                existing.posX(), existing.posY(), existing.zIndex(),
                existing.createdBy(), existing.createdAt(), now,
                resolvedOriginalLang
        );
        nodeRepository.update(updated);

        // ADR-043 Amendment 3 (22.d) - audit UPDATE с FieldDiff для изменившихся полей
        Map<String, AuditLogService.FieldDiff> diff = AuditLogService.diff()
                .compare("content", existing.content(), resolvedContent)
                .compare("originalLang", existing.originalLang(), resolvedOriginalLang)
                .build();
        if (!diff.isEmpty()) {
            auditLogService.logUpdate(AuditEntityType.NODE, nodeId,
                    AuditEntityType.TOPIC, existing.topicId(), userId, diff);
        }

        return updated;
    }

    @Transactional
    public Node updateContent(UUID nodeId, String newContent, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);
        return updateContent(nodeId, newContent, userId);
    }

    /**
     * Расширенная перегрузка с originalLang. Каждый параметр-box - либо
     * {@link NoChange#INSTANCE} (не трогаем), либо строка (новое значение),
     * либо null (очистить).
     */
    @Transactional
    public Node updateContent(UUID nodeId, Object newContentBox,
                              Object newOriginalLang, UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);
        return updateContent(nodeId, newContentBox, newOriginalLang, userId);
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
        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("nodeType", existing.nodeType().name())
                .put("content", existing.content())
                .build();
        auditLogService.logDelete(AuditEntityType.NODE, nodeId,
                AuditEntityType.TOPIC, existing.topicId(), userId, snapshot);

        deleteNode(nodeId);
    }

    /**
     * Групповое удаление узлов одним вызовом. Backlog «Bulk audit log
     * consolidation» - вместо N отдельных {@link #deleteNode(UUID, UUID, String)}
     * (каждый пишет audit row → N rows в admin UI как 50 несвязанных событий)
     * один service-метод пишет один {@link AuditAction#BULK_DELETE} per
     * topic с массивом entityIds.
     *
     * <p>Семантика:
     * <ul>
     *   <li>Все узлы должны принадлежать одному {@code topicId} - иначе
     *       {@link IllegalArgumentException}. Single-topic ограничение -
     *       чтобы один audit row имел один parent (topic) и один
     *       permission-чек ({@code assertCanWrite}).</li>
     *   <li>Корневые узлы (равные {@code topic.root_node_id}) пропускаются
     *       и возвращаются в {@code skippedRootIds} - не fail'ят весь
     *       запрос (UX: «удалил 47 из 50, 3 корневых пропущено»).</li>
     *   <li>Узлы которых нет в БД (стрейловый id от frontend) -
     *       {@link NodeNotFoundException} fail'ит транзакцию целиком
     *       (consistency: либо удаляем всё указанное, либо ничего).</li>
     *   <li>Один пересчёт статусов на topic в конце - не N.</li>
     *   <li>Audit row пишется ТОЛЬКО если хоть один узел реально удалён
     *       (skippedRootIds non-empty + deletedIds empty - audit не
     *       пишется, операция no-op).</li>
     * </ul>
     *
     * @return {@code (deletedIds, skippedRootIds)}
     */
    @Transactional
    public BulkDeleteResult bulkDeleteNodes(List<UUID> nodeIds, UUID userId, String role) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("nodeIds не должен быть пустым");
        }
        // dedup сохраняя порядок - protect от случайного дубля в запросе
        Set<UUID> uniqueIds = new LinkedHashSet<>(nodeIds);

        // загружаем все узлы за один проход + валидируем существование
        List<Node> nodes = new ArrayList<>(uniqueIds.size());
        for (UUID id : uniqueIds) {
            Node n = nodeRepository.findById(id)
                    .orElseThrow(() -> new NodeNotFoundException(id));
            nodes.add(n);
        }

        // все узлы должны быть из одного topic - иначе один permission
        // check + один audit parent не покрывают корректно
        UUID topicId = nodes.get(0).topicId();
        for (Node n : nodes) {
            if (!topicId.equals(n.topicId())) {
                throw new IllegalArgumentException(
                        "Все узлы в bulk запросе должны принадлежать одной теме");
            }
        }
        permissionService.assertCanWrite(topicId, userId, role);

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));

        // фильтруем корневые - не fail'им весь запрос
        List<UUID> deletedIds = new ArrayList<>();
        List<UUID> skippedRootIds = new ArrayList<>();
        // snapshots: id → {nodeType, content} - в audit row.snapshots
        Map<String, Object> snapshots = new LinkedHashMap<>();
        for (Node n : nodes) {
            if (n.id().equals(topic.rootNodeId())) {
                skippedRootIds.add(n.id());
                continue;
            }
            nodeRepository.deleteById(n.id());
            deletedIds.add(n.id());
            snapshots.put(n.id().toString(), AuditLogService.snapshot()
                    .put("nodeType", n.nodeType().name())
                    .put("content", n.content())
                    .build());
        }

        if (!deletedIds.isEmpty()) {
            // один audit row на всю bulk операцию (не N), один пересчёт статусов
            auditLogService.logBulkDelete(AuditEntityType.NODE,
                    AuditEntityType.TOPIC, topicId, userId, deletedIds, snapshots);
            statusCalculationService.recalculateTopic(topicId);
        }

        return new BulkDeleteResult(deletedIds, skippedRootIds);
    }

    /**
     * Результат {@link #bulkDeleteNodes(List, UUID, String)}.
     */
    public record BulkDeleteResult(List<UUID> deletedIds, List<UUID> skippedRootIds) {
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
                existing.createdBy(), existing.createdAt(), existing.updatedAt(),
                existing.originalLang()
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
                existing.createdBy(), existing.createdAt(), existing.updatedAt(),
                existing.originalLang()
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

    /**
     * Валидация originalLang: nullable, при заданном значении ∈ {ar, ru, en}.
     */
    private void validateOriginalLang(String originalLang) {
        if (originalLang != null && !ALLOWED_ORIGINAL_LANGS.contains(originalLang)) {
            throw new IllegalArgumentException(
                    "Недопустимое значение originalLang: '" + originalLang
                            + "'. Допустимые: " + ALLOWED_ORIGINAL_LANGS);
        }
    }

    /**
     * Sentinel "field not changed in this PATCH" для перегрузок update -
     * отличить «не пришло в payload» от «пришло как null (очистить)».
     * Не enum, не singleton-Holder - простой sentinel object.
     */
    public static final class NoChange {
        public static final NoChange INSTANCE = new NoChange();
        private NoChange() {}
    }
}
