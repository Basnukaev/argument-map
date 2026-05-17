package ru.basnukaev.argumentmap.auth.web.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;
import ru.basnukaev.argumentmap.auth.domain.User;
import ru.basnukaev.argumentmap.auth.repository.UserRepository;

/**
 * Transitional dev/test fallback (ADR-040): если SecurityContext empty
 * и в запросе пришёл {@code X-User-Id} - пытаемся lookup user и ставим
 * principal. Активируется ТОЛЬКО в profile "local" или "test" - в prod
 * фильтр не bean'ится через {@code @Profile("local | test | dev")}.
 *
 * <p>Цель - existing integration tests и frontend dev (без login UI до
 * Этапа 21.b) продолжают работать без переписывания. После 21.b -
 * удалить фильтр + удалить роутер X-User-Id из CORS allowed-headers.
 */
@Component
@Profile({"local", "dev", "test"})
public class XUserIdAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(XUserIdAuthenticationFilter.class);
    public static final String HEADER = "X-User-Id";

    private final UserRepository userRepository;

    public XUserIdAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Если кто-то уже залогинился через JWT - не переопределяем
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        UUID userId;
        try {
            userId = UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            // Невалидный UUID - оставляем SecurityContext empty, 401 на дальнейшем уровне
            chain.doFilter(request, response);
            return;
        }
        // Сохраняем ADR-006 семантику: X-User-Id - сырое UUID без
        // валидации существования (старое поведение @CurrentUser). Если
        // user отсутствует - выставляем synthetic principal только с id,
        // не лезем в БД (existing IT через mocks Liquibase часто
        // создают user'ов через jdbcTemplate в @BeforeEach, не
        // обязательно до прохождения этого фильтра)
        User user = userRepository.findById(userId).orElse(null);
        AuthenticatedUser principal = user != null
                ? new AuthenticatedUser(user.id(), user.username(), user.email(), user.role())
                : new AuthenticatedUser(userId, null, null, "USER");
        String role = user != null ? user.role() : "USER";
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("X-User-Id fallback: principal={} (dev/test profile, dbHit={})",
                principal.id(), user != null);
        chain.doFilter(request, response);
    }
}
