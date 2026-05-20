package ru.basnukaev.argumentmap.auth.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.web.dto.PageRequest;
import ru.basnukaev.argumentmap.web.dto.PagedResponse;
import java.util.List;

import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.service.UserService;
import ru.basnukaev.argumentmap.auth.web.dto.ChangeRoleRequest;
import ru.basnukaev.argumentmap.auth.web.dto.UserResponse;
import ru.basnukaev.argumentmap.auth.web.security.SecurityContextUtils;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.exception.AdminOnlyException;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.service.AuditLogService;
import ru.basnukaev.argumentmap.service.AuditLogService.FieldDiff;

import java.util.Map;

/**
 * Admin user management REST endpoints. Phase A.4 (Vision 49d) —
 * первая операция: PATCH /api/v1/users/{id}/role для повышения/
 * понижения роли (ADMIN → STUDENT/SCHOLAR/ADMIN).
 *
 * <p>Все mutating операции требуют ADMIN роль (bubble через
 * {@link AdminOnlyException} 403 forbidden-admin-only). Контроль —
 * на controller-уровне через SecurityContextUtils.currentRoleOrAnonymous.
 * Service-слой принимает уже verified role в actorAdminId.
 *
 * <p>Audit log entry пишется на каждый role change — admin может
 * запросить через GET /api/v1/audit/me (свои действия) или
 * /api/v1/audit/admin?entityType=USER.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    public UserController(UserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    /**
     * Phase A.7: paginated list users для admin management page.
     * Filters: ?role= (whitelist USER/STUDENT/SCHOLAR/ADMIN),
     * ?q= (username OR email substring case-insensitive),
     * ?page=&?size= standard pagination.
     */
    @GetMapping
    public PagedResponse<UserResponse> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser UUID adminId) {
        String adminRole = SecurityContextUtils.currentRoleOrAnonymous();
        if (!UserRole.ADMIN.equals(adminRole)) {
            throw new AdminOnlyException(adminId);
        }
        PageRequest pr = PageRequest.from(page, size);
        List<UserResponse> items = userService.listUsersPage(role, q, pr.size(), pr.offset())
                .stream()
                .map(u -> new UserResponse(
                        u.id(), u.username(), u.email(), u.role(), u.enabled(),
                        u.createdAt(), u.updatedAt()))
                .toList();
        long total = userService.countUsers(role, q);
        return PagedResponse.of(items, pr.page(), pr.size(), total);
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable UUID id,
                                   @Valid @RequestBody ChangeRoleRequest request,
                                   @CurrentUser UUID adminId) {
        String adminRole = SecurityContextUtils.currentRoleOrAnonymous();
        if (!UserRole.ADMIN.equals(adminRole)) {
            throw new AdminOnlyException(adminId);
        }
        // Snapshot before для audit log
        User before = userService.getById(id);
        String oldRole = before.role();

        User updated = userService.updateRole(adminId, id, request.newRole());

        // Audit log entry - если role реально изменилась. logUpdate с
        // entityType="USER" + fieldChanges {role: {old, new}}. Без parent
        // (USER - root entity). Find через GET /audit/admin?entityType=USER
        if (!oldRole.equals(updated.role())) {
            auditLogService.logUpdate("USER", id, null, null, adminId,
                    Map.of("role", new FieldDiff(oldRole, updated.role())));
        }

        return new UserResponse(
                updated.id(), updated.username(), updated.email(),
                updated.role(), updated.enabled(),
                updated.createdAt(), updated.updatedAt()
        );
    }
}
