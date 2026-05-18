package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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

    // Whitelist для bilingual полей (миграция 44). Должны совпадать с
    // CHECK constraints в БД. Валидация на уровне сервиса даёт чистый
    // 400 IllegalArgumentException вместо 500 DataIntegrityViolation.
    private static final Set<String> ALLOWED_TRANSLATION_LANGS = Set.of("ru", "en");
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
        return createNode(topicId, type, content, null, null, null, userId);
    }

    /**
     * Создание узла с bilingual-полями. translation/translationLang/originalLang
     * опциональны (null = поле не задано). Валидация:
     * <ul>
     *   <li>translation NOT NULL → translationLang обязателен и ∈ {ru, en}</li>
     *   <li>originalLang (если задан) ∈ {ar, ru, en}</li>
     * </ul>
     *
     * <p>Legacy перегрузка без bilingual (createNode без translation*) идёт
     * сюда с null'ами - backward-compat для существующих callers (TopicService
     * createTopic root question, TopicImportService и т.д.).
     */
    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content,
                           String translation, String translationLang, String originalLang,
                           UUID userId) {
        // legacy перегрузка - без permission check (для тестов и
        // batch importers). REST endpoint должен использовать перегрузку
        // с role.
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
        validateBilingual(translation, translationLang, originalLang);

        Instant now = Instant.now();
        Node node = new Node(
                UUID.randomUUID(), topicId, type, content,
                NodeStatus.UNVERIFIED, null, null, 0,
                userId, now, now,
                translation, translationLang, originalLang
        );
        nodeRepository.save(node);

        // ADR-043 Amendment 3 (22.d) - audit CREATE с topicId как parent
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeType", type.name());
        snapshot.put("content", content);
        if (translation != null) {
            snapshot.put("translation", translation);
            snapshot.put("translationLang", translationLang);
        }
        if (originalLang != null) {
            snapshot.put("originalLang", originalLang);
        }
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

    @Transactional
    public Node createNode(UUID topicId, NodeType type, String content,
                           String translation, String translationLang, String originalLang,
                           UUID userId, String role) {
        permissionService.assertCanWrite(topicId, userId, role);
        return createNode(topicId, type, content, translation, translationLang, originalLang, userId);
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
                existing.translation(), existing.translationLang(), existing.originalLang()
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
        return updateContent(nodeId, newContent, NoChange.INSTANCE, NoChange.INSTANCE, NoChange.INSTANCE, userId);
    }

    /**
     * Расширенный update - помимо content может опционально обновить
     * translation / translationLang / originalLang. Если параметр - {@link NoChange#INSTANCE},
     * соответствующее поле в БД остаётся прежним. Если параметр явно null
     * (через перегрузку без NoChange либо через REST DTO с явным null) -
     * поле очищается.
     *
     * <p>Revision записывается только для изменения content - translation/lang
     * не входят в историю содержательных изменений (это metadata). Решение
     * можно пересмотреть когда станет важно отслеживать перевод как версионный
     * артефакт.
     */
    @Transactional
    public Node updateContent(UUID nodeId, Object newContentBox,
                              Object newTranslation, Object newTranslationLang, Object newOriginalLang,
                              UUID userId) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        String resolvedContent = (newContentBox instanceof NoChange)
                ? existing.content()
                : (String) newContentBox;
        String resolvedTranslation = (newTranslation instanceof NoChange)
                ? existing.translation()
                : (String) newTranslation;
        String resolvedTranslationLang = (newTranslationLang instanceof NoChange)
                ? existing.translationLang()
                : (String) newTranslationLang;
        String resolvedOriginalLang = (newOriginalLang instanceof NoChange)
                ? existing.originalLang()
                : (String) newOriginalLang;

        validateBilingual(resolvedTranslation, resolvedTranslationLang, resolvedOriginalLang);

        Instant now = Instant.now();
        boolean contentChanged = !java.util.Objects.equals(existing.content(), resolvedContent);
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
                resolvedTranslation, resolvedTranslationLang, resolvedOriginalLang
        );
        nodeRepository.update(updated);

        // ADR-043 Amendment 3 (22.d) - audit UPDATE с FieldDiff для изменившихся полей
        Map<String, AuditLogService.FieldDiff> diff = new LinkedHashMap<>();
        if (contentChanged) {
            diff.put("content", new AuditLogService.FieldDiff(
                    existing.content(), resolvedContent));
        }
        if (!java.util.Objects.equals(existing.translation(), resolvedTranslation)) {
            diff.put("translation", new AuditLogService.FieldDiff(
                    existing.translation(), resolvedTranslation));
        }
        if (!java.util.Objects.equals(existing.translationLang(), resolvedTranslationLang)) {
            diff.put("translationLang", new AuditLogService.FieldDiff(
                    existing.translationLang(), resolvedTranslationLang));
        }
        if (!java.util.Objects.equals(existing.originalLang(), resolvedOriginalLang)) {
            diff.put("originalLang", new AuditLogService.FieldDiff(
                    existing.originalLang(), resolvedOriginalLang));
        }
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
     * Расширенная перегрузка с bilingual-полями. Каждый параметр-box -
     * либо {@link NoChange#INSTANCE} (не трогаем), либо строка (новое
     * значение), либо null (очистить). Для контроллера: используем когда
     * REST request явно передал поле (даже null) либо не передал вообще.
     */
    @Transactional
    public Node updateContent(UUID nodeId, Object newContentBox,
                              Object newTranslation, Object newTranslationLang, Object newOriginalLang,
                              UUID userId, String role) {
        Node existing = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        permissionService.assertCanWrite(existing.topicId(), userId, role);
        return updateContent(nodeId, newContentBox,
                newTranslation, newTranslationLang, newOriginalLang, userId);
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
                existing.createdBy(), existing.createdAt(), existing.updatedAt(),
                existing.translation(), existing.translationLang(), existing.originalLang()
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
                existing.translation(), existing.translationLang(), existing.originalLang()
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
     * Валидация bilingual-полей.
     * <ul>
     *   <li>translation NOT NULL → translationLang обязателен</li>
     *   <li>translationLang (если задан) ∈ {ru, en}</li>
     *   <li>originalLang (если задан) ∈ {ar, ru, en}</li>
     * </ul>
     */
    private void validateBilingual(String translation, String translationLang, String originalLang) {
        if (translation != null && !translation.isEmpty() && (translationLang == null || translationLang.isBlank())) {
            throw new IllegalArgumentException(
                    "translationLang обязателен когда translation задан");
        }
        if (translationLang != null && !ALLOWED_TRANSLATION_LANGS.contains(translationLang)) {
            throw new IllegalArgumentException(
                    "Недопустимое значение translationLang: '" + translationLang
                            + "'. Допустимые: " + ALLOWED_TRANSLATION_LANGS);
        }
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
