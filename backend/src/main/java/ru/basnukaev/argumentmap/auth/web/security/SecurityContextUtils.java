package ru.basnukaev.argumentmap.auth.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.UserRole;

/**
 * Утилиты для чтения principal/role из SecurityContext.
 *
 * <p>Используется в Service-слое (ADR-043) когда нужно проверять
 * permissions с учётом ADMIN-bypass. @CurrentUser даёт UUID, но не
 * role - role читается оттуда же где принципал
 * ({@link AuthenticatedUser#role()}).
 *
 * <p>Возвращает {@link UserRole#USER} по умолчанию если SecurityContext
 * empty или principal не наш тип - это безопасный fallback (нет ADMIN
 * bypass для unknown user).
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    public static String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return UserRole.USER;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user && user.role() != null) {
            return user.role();
        }
        return UserRole.USER;
    }
}
