package ru.basnukaev.argumentmap.auth.web.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ru.basnukaev.argumentmap.auth.service.JwtService;
import ru.basnukaev.argumentmap.exception.InvalidTokenException;

/**
 * Чтение Authorization: Bearer &lt;token&gt; и установка principal в
 * SecurityContext (ADR-040). Если токен отсутствует - фильтр пропускает
 * запрос дальше (последующие фильтры либо разрешат, либо 401 через
 * AuthenticationEntryPoint).
 *
 * <p>На любую ошибку валидации - тоже пропускаем (SecurityContext
 * остаётся empty). 401 выдаётся уже на уровне AccessDecisionManager
 * через AuthenticationEntryPoint - единая точка ответственности.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String type = jwtService.extractTokenType(token);
            if (!JwtService.TYPE_ACCESS.equals(type)) {
                // refresh-токен на API endpoints не допустим
                log.debug("Получен не-access токен ({}) на API endpoint - игнорируем", type);
                chain.doFilter(request, response);
                return;
            }
            AuthenticatedUser principal = jwtService.validateToken(token);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (InvalidTokenException ex) {
            // Не падаем здесь - SecurityFilterChain решит 401 / permit
            log.debug("JWT validation failed: {}", ex.getMessage());
        }
        chain.doFilter(request, response);
    }
}
