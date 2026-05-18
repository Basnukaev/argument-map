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
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    /**
     * Возвращает role principal'а из SecurityContext.
     *
     * <p>Если контекст пустой / principal не {@link AuthenticatedUser} /
     * principal.role()==null (anonymous в dev-profile permitAll, либо
     * не пропущенный JWT) - возвращает {@link UserRole#USER}. Это
     * сознательный fallback на least-privilege: anonymous traffic
     * получает USER (без ADMIN bypass), downstream service-checks
     * могут опираться на role без null-проверок.
     *
     * <p>Code review round 5 #6 - rename из {@code currentRole()}
     * чтобы fallback-семантика была явной для читателя (не latent
     * footgun). При migration на actual auth везде (dropping
     * X-User-Id permitAll) - можно будет упростить на
     * {@code Optional<String>} вариант. Пока fallback на USER -
     * единственно совместимый с dev/test profile где Security empty.
     */
    public static String currentRoleOrAnonymous() {
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
