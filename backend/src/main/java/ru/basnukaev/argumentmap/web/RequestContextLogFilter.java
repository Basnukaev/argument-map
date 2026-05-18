package ru.basnukaev.argumentmap.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;

/**
 * Кладёт {@code requestId} (UUID-v4) и {@code userId} (если есть в
 * SecurityContext) в SLF4J MDC. Логгер выводит их в каждой строке через
 * pattern {@code [%X{requestId}] [%X{userId}]} - без MDC лог пишет
 * `[]` (Logback default behaviour).
 *
 * <p>Цель - грепаемость логов по конкретному request'у / user'у.
 * Без этого один failed POST путается со всеми параллельными запросами,
 * trace разворачивается по timestamp + thread - неудобно при множестве
 * concurrent users или crawl shamela. С {@code requestId} один curl =
 * один UUID = одна grep-команда.
 *
 * <p>Order = HIGHEST_PRECEDENCE + 10 - запускается одним из первых,
 * чтобы все нижестоящие фильтры (JWT, X-User-Id, security checks)
 * уже писали в логи с заполненным requestId. SecurityContext проверяем
 * лениво - в первом запуске фильтра он ещё пустой, в downstream
 * фильтрах JWT уже выставил principal; для логов достаточно requestId
 * (userId появится в pattern когда auth filter сработает).
 *
 * <p>{@code finally MDC.clear()} обязательно - thread reuse в Tomcat
 * pool: без cleanup один user'ский id протёчёт в следующий запрос
 * другого user'а в этом thread'е.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestContextLogFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        // Add to response header чтобы клиент мог сослаться на конкретный
        // request при bug report. X-Request-Id - de facto convention.
        response.setHeader("X-Request-Id", requestId);
        try {
            // Auth filter ставит principal позже в цепочке - но MDC
            // pattern lazy-resolves; logger вечером прочитает MDC из
            // того же thread'а, principal к этому моменту уже на месте.
            // Ставим userId здесь только если он уже доступен (например,
            // ServletAsync re-dispatch куда контекст пробрасывается).
            populateUserIdIfAvailable();
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private void populateUserIdIfAvailable() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user && user.id() != null) {
            MDC.put(MDC_USER_ID, user.id().toString());
        }
    }
}
