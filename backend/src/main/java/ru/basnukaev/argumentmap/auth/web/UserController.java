package ru.basnukaev.argumentmap.auth.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
