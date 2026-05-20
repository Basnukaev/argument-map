package ru.basnukaev.argumentmap.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;
import ru.basnukaev.argumentmap.exception.EmailAlreadyTakenException;
import ru.basnukaev.argumentmap.exception.UserNotFoundException;
import ru.basnukaev.argumentmap.exception.UsernameAlreadyTakenException;

/**
 * Управление пользователями (ADR-040). Не отвечает за login flow -
 * это {@link AuthService}. UserService - registration / lookup /
 * enable-disable / password change. Все мутирующие методы транзакционны.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Регистрация нового пользователя. Email + username проверяются на
     * уникальность до записи. Password хешируется BCrypt. Role по
     * умолчанию USER.
     */
    @Transactional
    public User register(String email, String username, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            // security: не раскрываем email в сообщении клиенту - enumeration hardening.
            // для server-side debugging логируем WARN
            log.warn("Попытка регистрации с уже занятым email (masked): {}",
                    email.replaceAll("(?<=.{2}).(?=.*@)", "*"));
            throw new EmailAlreadyTakenException("Email уже занят");
        }
        if (userRepository.existsByUsername(username)) {
            // security: не раскрываем username в сообщении клиенту
            log.warn("Попытка регистрации с уже занятым username: {}", username);
            throw new UsernameAlreadyTakenException("Имя пользователя уже занято");
        }
        Instant now = Instant.now();
        User user = new User(
                UUID.randomUUID(),
                username,
                email,
                passwordEncoder.encode(rawPassword),
                UserRole.USER,
                true,
                now,
                now
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + id));
    }

    /**
     * Vision 49d Phase A.4 — admin-only updating роли пользователя.
     * Permission check (ADMIN role) — в caller (UserController через
     * PermissionService.assertHasRoleAtLeast). Здесь только domain
     * валидация (newRole в whitelist + user exists).
     *
     * <p>Не позволяет admin'у downgrade самого себя в non-ADMIN
     * (защищает от accidental lockout: последний ADMIN downgrade
     * сам себя — никто не сможет обратить). Этот guard семантический,
     * не security — если admin'ов 2+, downgrade одного безопасен,
     * но мы не проверяем «есть ли ещё ADMIN'ы» (требует extra query
     * на каждый call). Self-downgrade прямой не разрешён вообще —
     * admin может downgrade'ить только других admin'ов.
     */
    @Transactional
    public User updateRole(UUID actorAdminId, UUID targetUserId, String newRole) {
        if (!UserRole.isValid(newRole)) {
            throw new IllegalArgumentException("Invalid role: " + newRole
                    + ". Must be one of " + UserRole.ALL);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + targetUserId));
        if (actorAdminId.equals(targetUserId) && !UserRole.ADMIN.equals(newRole)) {
            throw new IllegalArgumentException(
                    "ADMIN не может понизить свою собственную роль (защита от lockout)");
        }
        if (target.role().equals(newRole)) {
            // no-op - early return without UPDATE
            return target;
        }
        log.info("Role change: user={} oldRole={} newRole={} actorAdmin={}",
                targetUserId, target.role(), newRole, actorAdminId);
        userRepository.updateRole(targetUserId, newRole);
        return userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден после update: " + targetUserId));
    }

    /**
     * Paginated user listing для admin. ADMIN-only check - в caller.
     * Phase A.7.
     */
    @Transactional(readOnly = true)
    public java.util.List<User> listUsersPage(String role, String q, int limit, int offset) {
        return userRepository.findPage(role, q, limit, offset);
    }

    @Transactional(readOnly = true)
    public long countUsers(String role, String q) {
        return userRepository.countFiltered(role, q);
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new UserNotFoundException("Пользователь не найден: " + userId);
        }
        userRepository.setEnabled(userId, enabled);
    }
}
