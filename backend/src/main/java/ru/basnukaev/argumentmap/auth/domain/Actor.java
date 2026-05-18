package ru.basnukaev.argumentmap.auth.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object для «кто действует» в системе. Заменяет повсеместный pattern
 * передачи `(UUID userId, String role)` через десятки сигнатур сервисов
 * (см. backend architecture audit 2026-05-18 finding 2).
 *
 * <p>Введён additive - старые перегрузки `(UUID, String)` остаются работать;
 * новые сервисы / переписки используют {@code Actor}. Постепенный rollout.
 *
 * <p>Семантика: actor = идентичность (userId) + полномочия (role). Если в
 * будущем понадобится audit-friendly username, requestId, clientId - они
 * добавятся сюда без обновления десятков сигнатур.
 *
 * <p>Не путать с {@link AuthenticatedUser} - тот principal внутри
 * SecurityContext (содержит email, username для UI). {@code Actor} -
 * урезанная projection того что нужно сервисам для permission checks.
 */
public record Actor(UUID userId, String role) {

    public Actor {
        Objects.requireNonNull(userId, "userId не может быть null");
        Objects.requireNonNull(role, "role не может быть null");
        if (!UserRole.isValid(role)) {
            throw new IllegalArgumentException(
                    "невалидное значение role: '" + role + "', допустимые: USER/ADMIN");
        }
    }

    /**
     * Factory для обычного пользователя (role=USER).
     */
    public static Actor user(UUID userId) {
        return new Actor(userId, UserRole.USER);
    }

    /**
     * Factory для администратора (role=ADMIN). Используется в admin tooling
     * и интеграционных тестах.
     */
    public static Actor admin(UUID userId) {
        return new Actor(userId, UserRole.ADMIN);
    }

    /**
     * Создаёт Actor из principal'а в SecurityContext. Принципал гарантированно
     * имеет userId; role может быть null - тогда берём дефолт USER.
     */
    public static Actor from(AuthenticatedUser principal) {
        String effectiveRole = (principal.role() == null) ? UserRole.USER : principal.role();
        return new Actor(principal.id(), effectiveRole);
    }

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(role);
    }

    public boolean isUser() {
        return UserRole.USER.equals(role);
    }
}
