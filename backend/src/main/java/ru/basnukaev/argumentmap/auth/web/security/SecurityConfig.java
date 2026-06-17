package ru.basnukaev.argumentmap.auth.web.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;

/**
 * Конфигурация Spring Security (ADR-040). Stateless + JWT.
 *
 * <p>Chain ordering:
 * <ol>
 *   <li>{@link JwtAuthenticationFilter} - читает Bearer токен из header
 *   <li>{@link XUserIdAuthenticationFilter} - dev/test fallback, только
 *       если JWT ничего не поставил в SecurityContext
 *   <li>UsernamePasswordAuthenticationFilter (стандартный Spring) -
 *       наши custom фильтры стоят перед ним
 * </ol>
 *
 * <p>CORS, error responses, CSRF, session - все настроены под SPA + REST.
 * Permit-all: /auth/**, /actuator/health, OpenAPI docs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    /**
     * Optional - bean регистрируется только в profile local/dev/test.
     * В prod нет - и фильтр не подключается.
     */
    private final ObjectProvider<XUserIdAuthenticationFilter> xUserIdFilterProvider;
    private final boolean devOrTestProfile;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          ObjectProvider<XUserIdAuthenticationFilter> xUserIdFilterProvider,
                          Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.xUserIdFilterProvider = xUserIdFilterProvider;
        this.devOrTestProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equals("local") || p.equals("dev") || p.equals("test"))
                || environment.getActiveProfiles().length == 0; // default profile = local
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost=10 - дефолт BCrypt, разумный баланс безопасность/latency
        // (~100ms на hash на современном железе)
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF не нужен - JWT в header, не в cookie (refresh в HttpOnly cookie
                // сам по себе immune к CSRF поскольку refresh endpoint
                // явно требует Origin/Referer match, либо защищается на
                // фронте через token-scoped action)
                .csrf(csrf -> csrf.disable())
                // CORS делегируется WebMvcConfig.addCorsMappings - не дублируем
                .cors(cors -> {})
                // Stateless - никаких HttpSession
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // /api/v1/auth/me - всегда требует Bearer (даже в
                    // dev/test profile) - это endpoint про current user,
                    // без auth не имеет смысла
                    auth.requestMatchers("/api/v1/auth/me").authenticated();
                    // auth endpoints публичные (login/register/refresh)
                    auth.requestMatchers("/api/v1/auth/login",
                                         "/api/v1/auth/register",
                                         "/api/v1/auth/refresh",
                                         "/api/v1/auth/logout")
                            .permitAll();
                    // actuator endpoints обрабатывает отдельный
                    // ActuatorSecurityConfig chain (@Order(1)) - ADR-048.
                    // В prod profile - basic auth для всего кроме health/info,
                    // в dev/test/local - permitAll. Здесь правил не нужно
                    // OpenAPI / Swagger - permit для dev tooling
                    auth.requestMatchers("/v3/api-docs/**",
                                         "/swagger-ui/**",
                                         "/swagger-ui.html")
                            .permitAll();
                    // CORS preflight - всегда permit
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // Guest view (roadmap 49.G / Vision 49d Section 2.5):
                    // read-only GET публичного контента доступен анониму во
                    // ВСЕХ профилях (включая prod). Покрывает темы, хадисы
                    // (+ рави), библиотеку (+ PDF stream), Q&A read. PRIVATE-
                    // контент НЕ раскрывается: permitAll снимает только
                    // Spring-уровень auth-гейт, а RBAC visibility-фильтр
                    // (ADR-043) живёт в service-слое и сам обрабатывает
                    // anonymous (userId=null через currentUserIdOrNull) —
                    // аноним видит только PUBLIC, PRIVATE/SHARED → 403/404.
                    // Мутации (POST/PATCH/DELETE) сюда не попадают (matcher
                    // привязан к GET) → падают в anyRequest().authenticated().
                    // /admin/** и /auth/me тоже не матчатся этими паттернами.
                    auth.requestMatchers(HttpMethod.GET,
                                         "/api/v1/topics/**",
                                         "/api/v1/hadith/**",
                                         "/api/v1/library/books/**",
                                         "/api/v1/library/pages/**",
                                         "/api/v1/questions/**")
                            .permitAll();
                    // ADR-040 transitional: в dev/local/test profile все
                    // /api/** endpoints публичные. Это покрывает 60+
                    // existing IT тестов которые до Этапа 21 не передавали
                    // X-User-Id (либо передавали через @CurrentUser - но
                    // существующее поведение было «query param userId
                    // если не передан → MissingUserHeaderException 400»,
                    // а не 401). XUserIdAuthenticationFilter всё равно
                    // выставит principal для @CurrentUser параметров
                    // когда header есть. В prod profile блок не
                    // активируется - все mutating требуют auth.
                    // После Этапа 21.b (frontend login UI) - убрать ветку
                    // вместе с XUserIdAuthenticationFilter.
                    if (devOrTestProfile) {
                        auth.requestMatchers("/api/**").permitAll();
                    }
                    // всё остальное - аутентифицированно
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                // Order: Rate limit (ADR-046) → JWT → UsernamePasswordAuthenticationFilter.
                // Rate limit ПЕРЕД JWT - блокируем brute-force до bcrypt /
                // DB lookup; применяется только к /auth/login и /auth/register;
                // для остальных endpoints filter no-op.
                //
                // Anchor для обоих - стандартный UsernamePasswordAuthenticationFilter.
                // Spring Security не знает order у custom JwtAuthenticationFilter -
                // нельзя использовать его как anchor. Two consecutive
                // addFilterBefore(filter, UsernamePasswordAuthenticationFilter) -
                // последний вставленный встаёт ближе к anchor, то есть выполнится
                // ПОСЛЕ rateLimit при request processing.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // HTTP security headers (cross-cutting audit #2) - delegated to
        // SecurityHeadersCustomizer (shared с ActuatorSecurityConfig чтобы
        // CSP / HSTS / Referrer / Permissions policy задавались в одном месте).
        // prodProfile = !devOrTestProfile - в этом chain используем расширенный
        // detection (empty profile считается local) для CSP/HSTS gating
        SecurityHeadersCustomizer.apply(http, !devOrTestProfile);

        // X-User-Id fallback фильтр - только если bean есть (dev/test profile).
        // Anchor - тот же UsernamePasswordAuthenticationFilter что у rateLimit
        // и jwt. Spring не знает order у custom JwtAuthenticationFilter.class -
        // нельзя использовать его как anchor (получим IllegalArgumentException
        // "filter does not have a registered order").
        //
        // Порядок execution: last addFilterBefore встаёт ближе к anchor, значит
        // выполняется ПОЗЖЕ всех предыдущих с тем же anchor. Registration order:
        // 1. rateLimit (first) - executes first (block brute force перед JWT)
        // 2. jwt (second) - executes after rateLimit, ставит principal если Bearer
        // 3. xUserId (third) - executes after jwt, fallback если SecurityContext пуст
        XUserIdAuthenticationFilter xUserIdFilter = xUserIdFilterProvider.getIfAvailable();
        if (xUserIdFilter != null) {
            http.addFilterBefore(xUserIdFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
