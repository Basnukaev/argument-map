package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.StatusAlgorithm;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.EdgeRepository;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.repository.TopicWithCounts;

@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;
    private final StatusCalculationService statusCalculationService;

    public TopicService(TopicRepository topicRepository, NodeRepository nodeRepository,
                        EdgeRepository edgeRepository,
                        PermissionService permissionService,
                        AuditLogService auditLogService,
                        StatusCalculationService statusCalculationService) {
        this.topicRepository = topicRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
        this.statusCalculationService = statusCalculationService;
    }

    /**
     * Создаёт тему с корневым узлом-вопросом одной транзакцией.
     * Из-за циркулярного FK topics↔nodes тема сначала пишется без
     * root_node_id, затем создаётся узел, затем FK дописывается.
     * Откат любого шага откатывает все три (см. gotchas.md).
     */
    @Transactional
    public Topic createTopic(String title, String description,
                             String rootQuestionContent, UUID userId) {
        return createTopic(title, description, rootQuestionContent,
                TopicVisibility.PRIVATE, userId);
    }

    @Transactional
    public Topic createTopic(String title, String description,
                             String rootQuestionContent, String visibility, UUID userId) {
        if (visibility == null) {
            visibility = TopicVisibility.PRIVATE;
        }
        if (!TopicVisibility.isValid(visibility)) {
            throw new IllegalArgumentException(
                    "Невалидное visibility: " + visibility
                            + " (ожидается PRIVATE/SHARED/PUBLIC)"
            );
        }
        Instant now = Instant.now();

        Topic topic = new Topic(
                UUID.randomUUID(), title, description,
                null, userId, now, visibility, StatusAlgorithm.MVP
        );
        topicRepository.save(topic);

        Node rootQuestion = new Node(
                UUID.randomUUID(), topic.id(), NodeType.QUESTION,
                rootQuestionContent, NodeStatus.UNVERIFIED,
                null, null, 0,
                userId, now, now,
                null
        );
        nodeRepository.save(rootQuestion);

        topicRepository.updateRootNodeId(topic.id(), rootQuestion.id());

        // ADR-043 Amendment 3 (22.d): audit CREATE для topic + root node
        Map<String, Object> topicSnapshot = AuditLogService.snapshot()
                .put("title", title)
                .put("description", description)
                .put("visibility", visibility)
                .build();
        auditLogService.logCreate(AuditEntityType.TOPIC, topic.id(), null, null,
                userId, topicSnapshot);
        Map<String, Object> nodeSnapshot = AuditLogService.snapshot()
                .put("nodeType", NodeType.QUESTION.name())
                .put("content", rootQuestionContent)
                .put("isRoot", true)
                .build();
        auditLogService.logCreate(AuditEntityType.NODE, rootQuestion.id(),
                AuditEntityType.TOPIC, topic.id(), userId, nodeSnapshot);

        return topicRepository.findById(topic.id()).orElseThrow();
    }

    /**
     * Перегрузка без role/userId - сохраняется для backward compat с
     * internal callers (TopicImportService, тесты которым не нужен
     * permission check). REST-controller должен использовать
     * {@link #getTopic(UUID, UUID, String)} c явной проверкой ADR-043.
     */
    @Transactional(readOnly = true)
    public Topic getTopic(UUID topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    @Transactional(readOnly = true)
    public Topic getTopic(UUID topicId, UUID userId, String role) {
        permissionService.assertCanRead(topicId, userId, role);
        return getTopic(topicId);
    }

    @Transactional(readOnly = true)
    public List<Topic> listTopics() {
        return topicRepository.findAll();
    }

    /**
     * Полный список всех тем без visibility-фильтра. Используется только
     * во внутренних janitors/migrations - REST endpoint /api/v1/topics
     * берёт {@link #listVisibleTopicsWithCounts(UUID, String)}.
     */
    @Transactional(readOnly = true)
    public List<TopicWithCounts> listTopicsWithCounts() {
        return topicRepository.findAllWithCounts();
    }

    /**
     * Список тем видимых пользователю (ADR-043). ADMIN получает все
     * темы без фильтра, USER - только PRIVATE owned + SHARED member +
     * PUBLIC.
     */
    @Transactional(readOnly = true)
    public List<TopicWithCounts> listVisibleTopicsWithCounts(UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return topicRepository.findAllWithCounts();
        }
        return topicRepository.findVisibleToUserWithCounts(userId);
    }

    /**
     * Пагинированный список тем с visibility-фильтром. ADMIN видит все,
     * USER - только видимые (см. {@link #listVisibleTopicsWithCounts}).
     *
     * @param visibility опционально - whitelist {@link TopicVisibility};
     *                   {@code null} = без фильтра. Валидируется до SQL
     */
    @Transactional(readOnly = true)
    public List<TopicWithCounts> listVisibleTopicsPage(UUID userId, String role,
                                                       String visibility,
                                                       int limit, int offset) {
        return listVisibleTopicsPage(userId, role, visibility, limit, offset, null);
    }

    /**
     * Vision 49d Section 2.1 — sort parameter overload. sort:
     * "recent" (default), "popular", "alphabetical". Invalid → fallback
     * recent silently. Whitelist в TopicRepository.orderByForSort.
     */
    @Transactional(readOnly = true)
    public List<TopicWithCounts> listVisibleTopicsPage(UUID userId, String role,
                                                       String visibility,
                                                       int limit, int offset,
                                                       String sort) {
        validateVisibility(visibility);
        if (UserRole.ADMIN.equals(role)) {
            return topicRepository.findAllPage(visibility, limit, offset, sort);
        }
        return topicRepository.findVisibleToUserPage(userId, visibility, limit, offset, sort);
    }

    @Transactional(readOnly = true)
    public long countVisibleTopics(UUID userId, String role, String visibility) {
        validateVisibility(visibility);
        if (UserRole.ADMIN.equals(role)) {
            return topicRepository.countAll(visibility);
        }
        return topicRepository.countVisibleToUser(userId, visibility);
    }

    private static void validateVisibility(String visibility) {
        if (visibility != null && !TopicVisibility.isValid(visibility)) {
            throw new IllegalArgumentException(
                    "Невалидное visibility-фильтр: " + visibility
                            + " (ожидается PRIVATE/SHARED/PUBLIC)"
            );
        }
    }

    @Transactional(readOnly = true)
    public TopicWithCounts getTopicWithCounts(UUID topicId) {
        return topicRepository.findByIdWithCounts(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    @Transactional(readOnly = true)
    public TopicWithCounts getTopicWithCounts(UUID topicId, UUID userId, String role) {
        permissionService.assertCanRead(topicId, userId, role);
        return getTopicWithCounts(topicId);
    }

    /**
     * Vision 49d Phase 2 - increment topic view counter. No-op если
     * topic не найден (idempotent: «view of deleted topic» допустим).
     */
    @Transactional
    public void incrementViewCount(UUID topicId) {
        topicRepository.incrementViewCount(topicId);
    }

    @Transactional
    public void deleteTopic(UUID topicId) {
        boolean removed = topicRepository.deleteById(topicId);
        if (!removed) {
            throw new TopicNotFoundException(topicId);
        }
    }

    /**
     * Удаление темы - только owner (или ADMIN). EDITOR этого не может,
     * даже на SHARED. См. ADR-043 матрицу.
     */
    @Transactional
    public void deleteTopic(UUID topicId, UUID userId, String role) {
        // Проверка существования + permission в одной транзакции.
        // Если темы нет - сначала проверка read (она бросит TopicNotFound),
        // потом assertIsOwner.
        Topic existing = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        permissionService.assertIsOwner(topicId, userId, role);

        Map<String, Object> snapshot = AuditLogService.snapshot()
                .put("title", existing.title())
                .put("description", existing.description())
                .put("visibility", existing.visibility())
                .build();
        auditLogService.logDelete(AuditEntityType.TOPIC, topicId, null, null,
                userId, snapshot);

        topicRepository.deleteById(topicId);
    }

    /**
     * Partial update title/description темы (backlog tech debt #10).
     *
     * <p>PATCH-семантика: {@code null} = поле не редактируется. Если
     * оба null - no-op (audit не пишется, recalculate не запускается).
     *
     * <p>Permissions: owner + EDITOR могут редактировать (SHARED тема
     * - EDITOR член может править content). PUBLIC viewer (без member
     * role) не может - {@code assertCanWrite} вернёт 403.
     *
     * <p>Audit: только реально изменившиеся поля попадают в diff
     * (через {@link AuditLogService#diff()} - {@code Objects.equals}
     * сравнение пропускает no-op).
     */
    @Transactional
    public Topic updateTopic(UUID topicId, String newTitle, String newDescription,
                             UUID userId, String role) {
        Topic existing = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        permissionService.assertCanWrite(topicId, userId, role);

        // PATCH-семантика: null = keep existing, non-null = replace
        String mergedTitle = newTitle != null ? newTitle : existing.title();
        String mergedDescription = newDescription != null ? newDescription : existing.description();

        Map<String, AuditLogService.FieldDiff> diff = AuditLogService.diff()
                .compare("title", existing.title(), mergedTitle)
                .compare("description", existing.description(), mergedDescription)
                .build();

        if (diff.isEmpty()) {
            // No-op: ничего не изменилось, не пишем audit и не дёргаем UPDATE
            return existing;
        }

        topicRepository.updateTitleAndDescription(topicId, mergedTitle, mergedDescription);
        auditLogService.logUpdate(AuditEntityType.TOPIC, topicId, null, null,
                userId, diff);
        return topicRepository.findById(topicId).orElseThrow();
    }

    /**
     * Меняет visibility темы (ADR-043) - только owner. EDITOR не может
     * (это privilege-escalation если бы мог - SHARED EDITOR сделал бы
     * себя owner через PUBLIC и обратно).
     */
    @Transactional
    public Topic updateVisibility(UUID topicId, String newVisibility,
                                  UUID userId, String role) {
        if (!TopicVisibility.isValid(newVisibility)) {
            throw new IllegalArgumentException(
                    "Невалидное visibility: " + newVisibility
            );
        }
        Topic existing = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        permissionService.assertIsOwner(topicId, userId, role);
        String oldVisibility = existing.visibility();
        topicRepository.updateVisibility(topicId, newVisibility);
        // audit только если действительно изменилось (избегаем шума)
        if (oldVisibility == null || !oldVisibility.equals(newVisibility)) {
            auditLogService.logVisibilityChange(AuditEntityType.TOPIC, topicId,
                    userId, oldVisibility, newVisibility);
        }
        return topicRepository.findById(topicId).orElseThrow();
    }

    /**
     * Bulk-clear координат узлов темы. Используется фронтом когда
     * пользователь хочет вернуться к авто-раскладке после ручных
     * перетаскиваний (preset-based layout system, см. elkLayout.ts).
     *
     * <p>Permissions: owner + EDITOR (assertCanWrite — те же что
     * patchTopic). PUBLIC viewer не может сбросить чужой topic.
     *
     * <p>Audit не пишется: позиции — UI affordance, не доменное
     * изменение (тот же argument что у updatePosition / updateZIndex
     * в NodeRepository).
     */
    @Transactional
    public void resetLayout(UUID topicId, UUID userId, String role) {
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
        permissionService.assertCanWrite(topicId, userId, role);
        nodeRepository.clearPositionsByTopic(topicId);
    }

    /**
     * Компактизирует z_index узлов и рёбер темы в одной транзакции.
     *
     * <p>После многократных вызовов bringToFront/sendToBack z_index может
     * разрастись до больших чисел (риск overflow). Этот метод читает все
     * узлы и рёбра темы упорядоченными по текущему z_index (тай-брейкер —
     * created_at) и переписывает их z_index в компактную последовательность
     * 0, 1, 2 ... N, сохраняя относительный порядок.
     *
     * <p>Permissions: owner + EDITOR (assertCanWrite — те же что resetLayout).
     * Audit не пишется: z_index — UI affordance, не доменное изменение.
     *
     * @return пара (nodesRenormalized, edgesRenormalized) — количество
     *         обновлённых записей (включая те у кого z_index не изменился,
     *         т.к. мы перезаписываем всю последовательность целиком)
     */
    @Transactional
    public RenormalizeResult renormalizeZIndex(UUID topicId, UUID userId, String role) {
        if (topicRepository.findById(topicId).isEmpty()) {
            throw new TopicNotFoundException(topicId);
        }
        permissionService.assertCanWrite(topicId, userId, role);

        List<Node> nodes = nodeRepository.findByTopicIdOrderedByZIndex(topicId);
        for (int i = 0; i < nodes.size(); i++) {
            nodeRepository.updateZIndex(nodes.get(i).id(), i);
        }

        List<Edge> edges = edgeRepository.findByTopicIdOrderedByZIndex(topicId);
        for (int i = 0; i < edges.size(); i++) {
            edgeRepository.updateZIndex(edges.get(i).id(), i);
        }

        return new RenormalizeResult(nodes.size(), edges.size());
    }

    /**
     * Результат {@link #renormalizeZIndex(UUID, UUID, String)}.
     */
    public record RenormalizeResult(int nodesRenormalized, int edgesRenormalized) {
    }

    /**
     * Меняет алгоритм пересчёта статусов узлов (ADR-044) - только owner.
     * Side effect: после смены сразу запускается пересчёт всех узлов под
     * новым алгоритмом. Это intentional - переключение значит «применить
     * новую семантику сейчас», иначе topic был бы в inconsistent состоянии
     * (статусы посчитаны старым алгоритмом, метка - на новом)
     */
    @Transactional
    public Topic updateStatusAlgorithm(UUID topicId, String newAlgorithm,
                                       UUID userId, String role) {
        if (!StatusAlgorithm.isValid(newAlgorithm)) {
            throw new IllegalArgumentException(
                    "Невалидный statusAlgorithm: " + newAlgorithm
                            + " (ожидается MVP или DUNG_GROUNDED)"
            );
        }
        Topic existing = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        permissionService.assertIsOwner(topicId, userId, role);
        String oldAlgorithm = existing.statusAlgorithm();
        if (oldAlgorithm != null && oldAlgorithm.equals(newAlgorithm)) {
            // no-op: возвращаем existing без пересчёта и audit-шума
            return existing;
        }
        topicRepository.updateStatusAlgorithm(topicId, newAlgorithm);
        // Audit изменения - field-level diff через UPDATE action (дешевле
        // чем плодить новый event type)
        Map<String, AuditLogService.FieldDiff> fieldChanges = AuditLogService.diff()
                .compare("statusAlgorithm", oldAlgorithm, newAlgorithm)
                .build();
        auditLogService.logUpdate(AuditEntityType.TOPIC, topicId, null, null,
                userId, fieldChanges);
        // Side effect - сразу применяем новый алгоритм. Текущая транзакция
        // продолжается, recalculate тоже в ней (см. javadoc StatusCalculation
        // Service - не имеет своего @Transactional, наследует caller'а)
        statusCalculationService.recalculateTopic(topicId);
        return topicRepository.findById(topicId).orElseThrow();
    }
}
