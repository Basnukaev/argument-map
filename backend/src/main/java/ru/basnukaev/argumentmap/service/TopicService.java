package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.StatusAlgorithm;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.repository.NodeRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.repository.TopicWithCounts;

@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final NodeRepository nodeRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;
    private final StatusCalculationService statusCalculationService;

    public TopicService(TopicRepository topicRepository, NodeRepository nodeRepository,
                        PermissionService permissionService,
                        AuditLogService auditLogService,
                        StatusCalculationService statusCalculationService) {
        this.topicRepository = topicRepository;
        this.nodeRepository = nodeRepository;
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
        validateVisibility(visibility);
        if (UserRole.ADMIN.equals(role)) {
            return topicRepository.findAllPage(visibility, limit, offset);
        }
        return topicRepository.findVisibleToUserPage(userId, visibility, limit, offset);
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
