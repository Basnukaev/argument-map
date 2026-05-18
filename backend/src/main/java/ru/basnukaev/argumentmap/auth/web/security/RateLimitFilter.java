package ru.basnukaev.argumentmap.auth.web.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.basnukaev.argumentmap.auth.config.RateLimitProperties;

/**
 * Sliding-window rate limit для auth endpoints (ADR-046, Security
 * backlog #1). Применяется только к {@code POST /api/v1/auth/login} и
 * {@code POST /api/v1/auth/register}.
 *
 * <p>Состояние per-IP в in-memory {@link ConcurrentHashMap}. При
 * превышении лимита в окне 1 минута - IP блокируется на
 * {@link RateLimitProperties#lockoutDuration} с возвратом 429 +
 * Retry-After header. По истечении lockout - счётчик сбрасывается.
 *
 * <p>Filter no-op если {@code auth.rate-limit.enabled=false} либо IP
 * в whitelist. Whitelist применяется ДО любых state mutations -
 * health probes / CI / smoke tests не засоряют Map.
 *
 * <p>IP extraction: предпочтение {@code X-Forwarded-For} (первый из
 * списка, если приложение за load balancer / CDN) → {@code X-Real-IP}
 * → {@code request.getRemoteAddr()}.
 *
 * <p>{@link Clock} injected (default {@code Clock.systemUTC()}) -
 * тесты могут подменить на mutable clock и fast-forward через
 * lockout без {@code Thread.sleep}.
 *
 * <p>Position в SecurityFilterChain: ставится BEFORE
 * {@link JwtAuthenticationFilter} - блокируем brute force до bcrypt
 * / DB lookup, экономия CPU при атаке.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Path login endpoint - точное совпадение, не префикс. */
    static final String PATH_LOGIN = "/api/v1/auth/login";

    /** Path register endpoint. */
    static final String PATH_REGISTER = "/api/v1/auth/register";

    /** Окно sliding window. Не делаем configurable - 1 минута фиксированно (OWASP baseline). */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** Idle threshold для cleanup - после этого без активности entry evict. */
    private static final Duration IDLE_EVICT = Duration.ofHours(1);

    /** Раз в N filter invocation запускаем lazy cleanup pass. Дешевле чем on-every-call. */
    private static final int CLEANUP_EVERY = 256;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Set<String> whitelist;

    /**
     * Per-IP state. Key - normalized client IP (string). Value -
     * mutable holder со списком timestamp'ов попыток и опциональным
     * lockoutUntil. Concurrent access защищён через monitor на
     * самом ClientState - один lock per IP, не глобальный.
     */
    private final ConcurrentHashMap<String, ClientState> stateByIp = new ConcurrentHashMap<>();

    /** Счётчик filter calls для periodic cleanup. */
    private volatile int callsSinceCleanup = 0;

    public RateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // HashSet для O(1) contains
        this.whitelist = new HashSet<>(properties.whitelistedIps());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        // Только POST на два конкретных path
        String path = request.getRequestURI();
        boolean isLogin = PATH_LOGIN.equals(path);
        boolean isRegister = PATH_REGISTER.equals(path);
        if (!isLogin && !isRegister) {
            chain.doFilter(request, response);
            return;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        if (whitelist.contains(ip)) {
            // Whitelist hit - propagate без state mutation
            chain.doFilter(request, response);
            return;
        }

        int limit = isLogin ? properties.loginAttemptsPerMinute() : properties.registerAttemptsPerMinute();
        String endpoint = isLogin ? "login" : "register";
        String stateKey = ip + ":" + endpoint;

        Instant now = clock.instant();
        ClientState state = stateByIp.computeIfAbsent(stateKey, k -> new ClientState());

        long retryAfterSeconds;
        boolean blocked;
        synchronized (state) {
            // Check & extend lockout если активен
            if (state.lockoutUntil != null) {
                if (now.isBefore(state.lockoutUntil)) {
                    blocked = true;
                    retryAfterSeconds = Math.max(1, Duration.between(now, state.lockoutUntil).toSeconds());
                } else {
                    // Lockout expired - reset
                    state.lockoutUntil = null;
                    state.attempts.clear();
                    blocked = false;
                    retryAfterSeconds = 0;
                }
            } else {
                blocked = false;
                retryAfterSeconds = 0;
            }

            if (!blocked) {
                // Evict timestamps старее окна
                Instant windowStart = now.minus(WINDOW);
                while (!state.attempts.isEmpty() && state.attempts.peekFirst().isBefore(windowStart)) {
                    state.attempts.pollFirst();
                }
                state.attempts.addLast(now);
                state.lastSeen = now;
                if (state.attempts.size() > limit) {
                    state.lockoutUntil = now.plus(properties.lockoutDuration());
                    blocked = true;
                    retryAfterSeconds = properties.lockoutDuration().toSeconds();
                    log.warn("Rate limit exceeded: ip={}, endpoint={}, attempts={}, lockoutSec={}",
                            ip, endpoint, state.attempts.size(), retryAfterSeconds);
                }
            }
        }

        // Lazy cleanup периодически - вне synchronized state
        maybeCleanup(now);

        if (blocked) {
            writeRateLimitedResponse(response, retryAfterSeconds, limit);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Resolve client IP с учётом X-Forwarded-For / X-Real-IP. Возвращает
     * никогда не {@code null} - либо реальный IP, либо
     * {@code request.getRemoteAddr()} (никогда не null в servlet-API).
     */
    static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 - берём первый (origin)
            int comma = xff.indexOf(',');
            String first = comma >= 0 ? xff.substring(0, comma) : xff;
            return normalizeIp(first.trim());
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return normalizeIp(real.trim());
        }
        return normalizeIp(request.getRemoteAddr());
    }

    /**
     * Нормализация IP: убираем порт из IPv4 ({@code 1.2.3.4:5678}) или
     * IPv6 в bracket-notation ({@code [::1]:5678} → {@code ::1}).
     * Защищает от обхода whitelist через "127.0.0.1:9999".
     */
    static String normalizeIp(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // IPv6 в bracket-notation [::1]:port
        if (raw.startsWith("[")) {
            int close = raw.indexOf(']');
            if (close > 0) {
                return raw.substring(1, close);
            }
        }
        // IPv4 с портом 1.2.3.4:5678 - один двоеточие. IPv6 без bracket -
        // несколько двоеточий, не трогаем (нет порта)
        int colons = 0;
        int lastColon = -1;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ':') {
                colons++;
                lastColon = i;
            }
        }
        if (colons == 1) {
            return raw.substring(0, lastColon);
        }
        return raw;
    }

    private void writeRateLimitedResponse(HttpServletResponse response,
                                          long retryAfterSeconds,
                                          int limit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Problem Details RFC 7807 inline - не делаем exception unwinding
        // чтобы не тратить cycles при атаке
        String body = String.format(Locale.ROOT,
                "{\"type\":\"https://argumentmap.example/errors/too-many-requests\","
                        + "\"title\":\"Слишком много попыток\","
                        + "\"status\":429,"
                        + "\"detail\":\"Превышен лимит %d попыток в минуту. Повторите через %d сек.\","
                        + "\"retryAfterSeconds\":%d}",
                limit, retryAfterSeconds, retryAfterSeconds);
        response.getWriter().write(body);
    }

    /**
     * Lazy cleanup stale entries. Запускается не на каждом вызове -
     * раз в {@link #CLEANUP_EVERY} filter invocations.
     */
    private void maybeCleanup(Instant now) {
        callsSinceCleanup++;
        if (callsSinceCleanup < CLEANUP_EVERY) {
            return;
        }
        callsSinceCleanup = 0;
        Instant evictBefore = now.minus(IDLE_EVICT);
        Iterator<Map.Entry<String, ClientState>> it = stateByIp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ClientState> e = it.next();
            ClientState s = e.getValue();
            synchronized (s) {
                Instant ls = s.lastSeen;
                // Не evict если ещё в lockout - даже если idle
                if (s.lockoutUntil != null && now.isBefore(s.lockoutUntil)) {
                    continue;
                }
                if (ls == null || ls.isBefore(evictBefore)) {
                    it.remove();
                }
            }
        }
    }

    /** Для тестов: forсиовать cleanup без ожидания threshold. */
    void cleanupNow() {
        callsSinceCleanup = CLEANUP_EVERY;
        maybeCleanup(clock.instant());
    }

    /** Для тестов: размер state map. */
    int stateSize() {
        return stateByIp.size();
    }

    /** Для тестов: очистить state (между тестами в одном Spring context). */
    void resetState() {
        stateByIp.clear();
        callsSinceCleanup = 0;
    }

    /**
     * Holder для per-IP rate-limit state. Mutating ops защищены
     * через {@code synchronized (state)}.
     */
    private static final class ClientState {
        /** Timestamp'ы попыток в окне 1 минута. Сортирован ascending. */
        final Deque<Instant> attempts = new ArrayDeque<>();
        /** Когда снимается lockout (null если не lock'нут). */
        Instant lockoutUntil;
        /** Время последнего access - для idle cleanup. */
        Instant lastSeen;
    }
}
