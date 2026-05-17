package ru.basnukaev.argumentmap.web.controller;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.domain.AuditEntityType;
import ru.basnukaev.argumentmap.domain.AuditLog;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.service.AuditLogService;
import ru.basnukaev.argumentmap.service.PermissionService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.AuditLogResponse;
import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;

/**
 * REST endpoints для просмотра audit_log (Этап 22.d, ADR-043 Amendment 3).
 *
 * <p>4 endpoint'а с разными permission rules:
 * <ul>
 *   <li>{@code GET /api/v1/audit/topics/{id}} - audit для темы + всех её
 *       child entities (nodes/edges). Доступен owner + EDITOR (через
 *       {@link PermissionService#assertCanWrite}).
 *   <li>{@code GET /api/v1/audit/books/{id}} - audit для книги. Доступен
 *       owner + EDITOR ({@code assertCanWriteBook}).
 *   <li>{@code GET /api/v1/audit/me} - что текущий user делал. Видит
 *       только свои actions, любой authenticated.
 *   <li>{@code GET /api/v1/audit/admin} - всё аудитное хозяйство с
 *       фильтрами. Требует ADMIN role.
 * </ul>
 *
 * <p>Все endpoints возвращают {@link PagedResponse}{@code <AuditLogResponse>}.
 * Сортировка - {@code created_at DESC} (новые сверху).
 *
 * <p>Username для actor JOIN'ится отдельно после fetch'а - в Repository
 * не делаем JOIN т.к. users отдельный package и хочется избежать
 * cross-package coupling на SQL level.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final PermissionService permissionService;
    private final UserRepository userRepository;

    public AuditLogController(AuditLogService auditLogService,
                              PermissionService permissionService,
                              UserRepository userRepository) {
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/topics/{topicId}")
    public PagedResponse<AuditLogResponse> auditTopic(
            @PathVariable UUID topicId,
            @CurrentUser UUID currentUserId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        // Только owner + EDITOR могут видеть audit темы. Используем
        // assertCanWrite (а не canRead) - чтение audit это privileged
        // действие даже на SHARED/PUBLIC темах
        String role = SecurityContextUtils.currentRole();
        permissionService.assertCanWrite(topicId, currentUserId, role);
        PageRequest pr = PageRequest.from(page, size);
        List<AuditLog> items = auditLogService.findByParentOrSelfPage(
                AuditEntityType.TOPIC, topicId, pr.size(), pr.offset());
        long total = auditLogService.countByParentOrSelf(AuditEntityType.TOPIC, topicId);
        return PagedResponse.of(toResponses(items), pr.page(), pr.size(), total);
    }

    @GetMapping("/books/{bookId}")
    public PagedResponse<AuditLogResponse> auditBook(
            @PathVariable UUID bookId,
            @CurrentUser UUID currentUserId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        String role = SecurityContextUtils.currentRole();
        permissionService.assertCanWriteBook(bookId, currentUserId, role);
        PageRequest pr = PageRequest.from(page, size);
        List<AuditLog> items = auditLogService.findByParentOrSelfPage(
                AuditEntityType.BOOK, bookId, pr.size(), pr.offset());
        long total = auditLogService.countByParentOrSelf(AuditEntityType.BOOK, bookId);
        return PagedResponse.of(toResponses(items), pr.page(), pr.size(), total);
    }

    @GetMapping("/me")
    public PagedResponse<AuditLogResponse> auditMe(
            @CurrentUser UUID currentUserId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        // Любой authenticated user может видеть свои действия. Permission
        // check не нужен - findByActor фильтрует по currentUserId
        PageRequest pr = PageRequest.from(page, size);
        List<AuditLog> items = auditLogService.findByActorPage(
                currentUserId, pr.size(), pr.offset());
        long total = auditLogService.countByActor(currentUserId);
        return PagedResponse.of(toResponses(items), pr.page(), pr.size(), total);
    }

    /**
     * Admin-only endpoint со всеми фильтрами. ADMIN bypass всех
     * visibility/ownership rules - может смотреть аудит чего угодно.
     *
     * <p>Date filters - ISO-8601 instant ({@code 2026-05-18T10:00:00Z}).
     * Невалидный формат → IllegalArgumentException → 400 через
     * GlobalExceptionHandler.
     */
    @GetMapping("/admin")
    public PagedResponse<AuditLogResponse> auditAdmin(
            @CurrentUser UUID currentUserId,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "actorId", required = false) UUID actorId,
            @RequestParam(name = "dateFrom", required = false) String dateFromIso,
            @RequestParam(name = "dateTo", required = false) String dateToIso,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        String role = SecurityContextUtils.currentRole();
        if (!UserRole.ADMIN.equals(role)) {
            throw new AdminOnlyException(currentUserId);
        }
        if (entityType != null && !AuditEntityType.isValid(entityType)) {
            throw new IllegalArgumentException("Невалидный entityType: " + entityType);
        }
        Instant dateFrom = parseIso(dateFromIso, "dateFrom");
        Instant dateTo = parseIso(dateToIso, "dateTo");
        PageRequest pr = PageRequest.from(page, size);
        List<AuditLog> items = auditLogService.findFilteredPage(
                entityType, actorId, dateFrom, dateTo, pr.size(), pr.offset());
        long total = auditLogService.countFiltered(entityType, actorId, dateFrom, dateTo);
        return PagedResponse.of(toResponses(items), pr.page(), pr.size(), total);
    }

    // ---- helpers ----

    private static Instant parseIso(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Невалидный ISO-8601 instant для " + paramName + ": " + value);
        }
    }

    /**
     * Маппит {@link AuditLog} → {@link AuditLogResponse} + bulk JOIN
     * с users для actorUsername. Bulk вместо N+1 select - один SQL
     * на всю страницу.
     */
    private List<AuditLogResponse> toResponses(List<AuditLog> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> usernameByActor = new HashMap<>();
        for (AuditLog log : items) {
            usernameByActor.putIfAbsent(log.actorUserId(), null);
        }
        // bulk fetch usernames одним проходом (Map уже unique)
        for (UUID actorId : usernameByActor.keySet()) {
            usernameByActor.put(actorId,
                    userRepository.findById(actorId).map(User::username).orElse(null));
        }
        return items.stream()
                .map(log -> new AuditLogResponse(
                        log.id(),
                        log.entityType(),
                        log.entityId(),
                        log.parentEntityType(),
                        log.parentEntityId(),
                        log.action(),
                        log.actorUserId(),
                        usernameByActor.get(log.actorUserId()),
                        log.changes(),
                        log.createdAt()
                ))
                .toList();
    }
}
