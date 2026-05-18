package ru.basnukaev.argumentmap.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.domain.AuditAction;
import ru.basnukaev.argumentmap.domain.AuditLog;
import ru.basnukaev.argumentmap.repository.AuditLogRepository;

/**
 * Сервис записи audit_log (Этап 22.d, ADR-043 Amendment 3).
 *
 * <p><b>Synchronous</b> запись в той же транзакции что и mutation - если
 * mutation rollback'нется, audit row тоже откатится (consistency). Если
 * insert audit упадёт - rollback всей транзакции включая mutation
 * (acceptable: лучше отказать чем сохранить mutation без audit trail).
 *
 * <p>JSON-сериализация changes/metadata через {@link ObjectMapper}. Если
 * сериализация падает (типа NPE) - log + swallow, audit row пишется без
 * changes (entity_type/entity_id/action всё ещё фиксируются). Иначе
 * сломанный domain-объект сломал бы main flow что хуже потери audit detail.
 *
 * <p>Manual logging (не Spring AOP) - явный контроль над тем что и куда
 * пишется + легко debug'ать. Каждый mutation-метод в существующих
 * сервисах добавляет 1 вызов {@code auditLogService.log*}.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog logCreate(String entityType, UUID entityId,
                              String parentEntityType, UUID parentEntityId,
                              UUID actorUserId, Object createdSnapshot) {
        Map<String, Object> changes = createdSnapshot == null
                ? null
                : Map.of("created", createdSnapshot);
        return save(entityType, entityId, parentEntityType, parentEntityId,
                AuditAction.CREATE, actorUserId, changes);
    }

    /**
     * Запись UPDATE-события с per-field diff.
     *
     * @param fieldChanges {@code Map<fieldName, [oldValue, newValue]>}.
     *                     {@code null}/empty - пишется action=UPDATE без changes
     */
    @Transactional
    public AuditLog logUpdate(String entityType, UUID entityId,
                              String parentEntityType, UUID parentEntityId,
                              UUID actorUserId,
                              Map<String, FieldDiff> fieldChanges) {
        Map<String, Object> changes = null;
        if (fieldChanges != null && !fieldChanges.isEmpty()) {
            changes = new LinkedHashMap<>();
            for (Map.Entry<String, FieldDiff> e : fieldChanges.entrySet()) {
                // HashMap (а не Map.of) - чтобы null-значения сохранялись в
                // jsonb как JSON null, а не как "" (см. M-1 audit 2026-05-18).
                // Семантика: null = поле было/стало absent, "" = поле было
                // пустой строкой. Map.of отвергает null'ы, заставляя писать
                // "" на нашей стороне - и теряется отличие
                Map<String, Object> oldNew = new LinkedHashMap<>();
                oldNew.put("old", e.getValue().oldValue());
                oldNew.put("new", e.getValue().newValue());
                changes.put(e.getKey(), oldNew);
            }
        }
        return save(entityType, entityId, parentEntityType, parentEntityId,
                AuditAction.UPDATE, actorUserId, changes);
    }

    @Transactional
    public AuditLog logDelete(String entityType, UUID entityId,
                              String parentEntityType, UUID parentEntityId,
                              UUID actorUserId, Object deletedSnapshot) {
        Map<String, Object> changes = deletedSnapshot == null
                ? null
                : Map.of("deleted", deletedSnapshot);
        return save(entityType, entityId, parentEntityType, parentEntityId,
                AuditAction.DELETE, actorUserId, changes);
    }

    @Transactional
    public AuditLog logVisibilityChange(String entityType, UUID entityId,
                                        UUID actorUserId,
                                        String oldVisibility, String newVisibility) {
        // null-safe map: HashMap чтобы null-значения сериализовались как
        // JSON null, а не как "" (см. logUpdate комментарий)
        Map<String, Object> oldNew = new LinkedHashMap<>();
        oldNew.put("old", oldVisibility);
        oldNew.put("new", newVisibility);
        Map<String, Object> changes = Map.of("visibility", oldNew);
        return save(entityType, entityId, null, null,
                AuditAction.VISIBILITY_CHANGE, actorUserId, changes);
    }

    @Transactional
    public AuditLog logMemberAdd(String memberEntityType, UUID memberId,
                                 String parentEntityType, UUID parentEntityId,
                                 UUID actorUserId,
                                 UUID userId, String role) {
        Map<String, Object> changes = Map.of(
                "userId", userId.toString(),
                "role", role
        );
        return save(memberEntityType, memberId, parentEntityType, parentEntityId,
                AuditAction.MEMBER_ADD, actorUserId, changes);
    }

    @Transactional
    public AuditLog logMemberRemove(String memberEntityType, UUID memberId,
                                    String parentEntityType, UUID parentEntityId,
                                    UUID actorUserId,
                                    UUID userId, String role) {
        Map<String, Object> changes = Map.of(
                "userId", userId.toString(),
                "role", role
        );
        return save(memberEntityType, memberId, parentEntityType, parentEntityId,
                AuditAction.MEMBER_REMOVE, actorUserId, changes);
    }

    @Transactional
    public AuditLog logMemberRoleChange(String memberEntityType, UUID memberId,
                                        String parentEntityType, UUID parentEntityId,
                                        UUID actorUserId,
                                        UUID memberUserId,
                                        String oldRole, String newRole) {
        // HashMap для role.old/new чтобы null сохранился как JSON null
        Map<String, Object> oldNewRole = new LinkedHashMap<>();
        oldNewRole.put("old", oldRole);
        oldNewRole.put("new", newRole);
        Map<String, Object> changes = Map.of(
                "userId", memberUserId.toString(),
                "role", oldNewRole
        );
        return save(memberEntityType, memberId, parentEntityType, parentEntityId,
                AuditAction.MEMBER_ROLE_CHANGE, actorUserId, changes);
    }

    // ---- READ-методы для controller ----

    @Transactional(readOnly = true)
    public List<AuditLog> findByParentOrSelfPage(String parentType, UUID parentId,
                                                 int limit, int offset) {
        return repository.findByParentOrSelfPage(parentType, parentId, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countByParentOrSelf(String parentType, UUID parentId) {
        return repository.countByParentOrSelf(parentType, parentId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByEntityPage(String entityType, UUID entityId,
                                           int limit, int offset) {
        return repository.findByEntityPage(entityType, entityId, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countByEntity(String entityType, UUID entityId) {
        return repository.countByEntity(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByActorPage(UUID actorId, int limit, int offset) {
        return repository.findByActorPage(actorId, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countByActor(UUID actorId) {
        return repository.countByActor(actorId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findFilteredPage(String entityType, UUID actorId,
                                           Instant dateFrom, Instant dateTo,
                                           int limit, int offset) {
        return repository.findFilteredPage(entityType, actorId, dateFrom, dateTo,
                limit, offset);
    }

    @Transactional(readOnly = true)
    public long countFiltered(String entityType, UUID actorId,
                              Instant dateFrom, Instant dateTo) {
        return repository.countFiltered(entityType, actorId, dateFrom, dateTo);
    }

    // ---- private ----

    private AuditLog save(String entityType, UUID entityId,
                          String parentEntityType, UUID parentEntityId,
                          String action, UUID actorUserId,
                          Map<String, Object> changes) {
        String changesJson = serializeOrLog(changes);
        AuditLog row = new AuditLog(
                UUID.randomUUID(),
                entityType,
                entityId,
                parentEntityType,
                parentEntityId,
                action,
                actorUserId,
                changesJson,
                null,
                Instant.now()
        );
        return repository.save(row);
    }

    /**
     * Если JSON-сериализация падает - log warning, возвращаем null
     * (audit row сохранится без changes detail). Лучше потерять detail
     * чем сломать main mutation.
     */
    private String serializeOrLog(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("audit log JSON serialization failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Пара значений до/после для конкретного field в UPDATE-событии.
     */
    public record FieldDiff(Object oldValue, Object newValue) {
    }

    // ---- helpers для snapshot building ----

    /**
     * Builder для snapshot Map с предсказуемым порядком вставки. Заменяет
     * boilerplate {@code new LinkedHashMap<>() + put + put + ...} который
     * повторяется в каждом mutation site (Book/Node/Edge/Topic/Question/Answer
     * services).
     *
     * <p>Использование:
     * <pre>{@code
     * Map<String, Object> snapshot = AuditLogService.snapshot()
     *     .put("title", book.title())
     *     .put("visibility", book.visibility())
     *     .build();
     * auditLogService.logDelete(BOOK, id, null, null, userId, snapshot);
     * }</pre>
     *
     * <p>{@code put} безопасно принимает null - сохраняет ключ с null-value
     * (важно для full snapshot чтобы видеть какие поля были null до удаления).
     * Для enum {@code .name()} вызывает caller - helper нейтрален к типу.
     */
    public static SnapshotBuilder snapshot() {
        return new SnapshotBuilder();
    }

    public static final class SnapshotBuilder {
        private final Map<String, Object> map = new LinkedHashMap<>();

        private SnapshotBuilder() {
        }

        public SnapshotBuilder put(String key, Object value) {
            map.put(key, value);
            return this;
        }

        public Map<String, Object> build() {
            return map;
        }
    }

    /**
     * Builder для per-field diff в UPDATE-событиях. Сравнивает before/after
     * через {@link Objects#equals} и добавляет {@link FieldDiff} только
     * если значения отличаются. Заменяет boilerplate из
     * {@code !Objects.equals(...) + diff.put(...)} которая повторяется в
     * 5+ сервисах.
     *
     * <p>Использование:
     * <pre>{@code
     * Map<String, FieldDiff> diff = AuditLogService.diff()
     *     .compare("title", before.title(), after.title())
     *     .compare("body", before.body(), after.body())
     *     .build();
     * if (!diff.isEmpty()) {
     *     auditLogService.logUpdate(...);
     * }
     * }</pre>
     */
    public static DiffBuilder diff() {
        return new DiffBuilder();
    }

    public static final class DiffBuilder {
        private final Map<String, FieldDiff> map = new LinkedHashMap<>();

        private DiffBuilder() {
        }

        public DiffBuilder compare(String fieldName, Object before, Object after) {
            if (!Objects.equals(before, after)) {
                map.put(fieldName, new FieldDiff(before, after));
            }
            return this;
        }

        public Map<String, FieldDiff> build() {
            return map;
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }
    }
}
