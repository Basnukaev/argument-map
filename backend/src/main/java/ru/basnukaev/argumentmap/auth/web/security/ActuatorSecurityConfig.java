package ru.basnukaev.argumentmap.auth.web.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Отдельный {@link SecurityFilterChain} для {@code /actuator/**} (ADR-048).
 *
 * <p>В prod profile все actuator endpoints кроме {@code /actuator/health}
 * и {@code /actuator/info} требуют basic auth (single in-memory user с
 * ролью {@code ACTUATOR}). Health и info <b>не требуют</b> basic auth -
 * load balancer liveness/readiness probes и CI/CD deploy verification.
 * Защита от reconnaissance leak: circuitbreakers/metrics/env содержат
 * версии backend, DB connection state, bean names.
 *
 * <p>В dev/test/local profile - all permitAll, чтобы разработчики
 * могли свободно curl'ить actuator endpoints без credentials.
 *
 * <p>Этот chain имеет {@code @Order(1)} - матчится первым по
 * {@code securityMatcher("/actuator/**")}, главный
 * {@link SecurityConfig} chain не пытается обрабатывать actuator.
 * In-memory {@link UserDetailsService} применяется локально (через
 * {@code http.userDetailsService(...)}) - не конфликтует с основным
 * JWT-based auth (последний вообще не использует UserDetailsService).
 */
@Configuration
public class ActuatorSecurityConfig {

    private final boolean prodProfile;
    private final String username;
    private final String password;

    public ActuatorSecurityConfig(Environment environment,
                                  @Value("${actuator.security.username:}") String username,
                                  @Value("${actuator.security.password:}") String password) {
        // Используется shared helper - тот же detection что в SecurityConfig
        // через SecurityHeadersCustomizer.isProdProfile (literal "prod" в
        // active profiles). Не передаём Environment через chain - всё фиксируется
        // в constructor flag, headers/auth ветка ниже использует boolean
        this.prodProfile = SecurityHeadersCustomizer.isProdProfile(environment);
        this.username = username;
        this.password = password;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // HTTP security headers - shared helper с main SecurityConfig.
        // Чтобы /actuator/health (доступный LB через HTTPS) отдавал
        // HSTS / CSP / Referrer / Permissions policy консистентно с
        // остальным трафиком. Иначе разные chain'ы дают разный набор
        // header'ов, путают penetration scanner'ы и нарушают существующие
        // SecurityHeadersIT
        SecurityHeadersCustomizer.apply(http, prodProfile);

        if (prodProfile) {
            // Prod fail-fast - credentials обязательны через env
            // ACTUATOR_USERNAME / ACTUATOR_PASSWORD. В dev падать тут не
            // надо (там profile != prod, ветка не активна)
            if (isBlank(username) || isBlank(password)) {
                throw new IllegalStateException(
                        "В prod profile actuator.security.username и actuator.security.password обязательны"
                                + " (env ACTUATOR_USERNAME / ACTUATOR_PASSWORD). Заданные значения пусты.");
            }
            UserDetails actuatorUser = User.builder()
                    .username(username)
                    // {noop} - plain text, не хеш. Single bootstrap user
                    // из env-переменной, без БД, без BCrypt - в prod
                    // credentials заданы один раз через secret manager /
                    // env, ротация через redeploy. BCrypt не даёт
                    // benefit в этой модели
                    .password("{noop}" + password)
                    .roles("ACTUATOR")
                    .build();
            UserDetailsService uds = new InMemoryUserDetailsManager(actuatorUser);

            // Локальный AuthenticationManager для этого chain. Глобальный
            // PasswordEncoder bean - BCryptPasswordEncoder (для основной
            // auth), он не понимает {noop} префикс - basic auth матчинг
            // упал бы 401. Используем DelegatingPasswordEncoder который
            // распознаёт {noop}, {bcrypt}, etc префиксы
            PasswordEncoder delegatingEncoder =
                    PasswordEncoderFactories.createDelegatingPasswordEncoder();
            // Конструктор DaoAuthenticationProvider(UserDetailsService) -
            // non-deprecated в Spring Security 6.3+ (старый setter
            // setUserDetailsService помечен deprecated)
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
            provider.setPasswordEncoder(delegatingEncoder);

            http
                    .authorizeHttpRequests(auth -> auth
                            // LB liveness/readiness probes - public
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info")
                            .permitAll()
                            // Всё остальное (metrics/circuitbreakers/env/...)
                            // требует ACTUATOR role - reconnaissance defence
                            .anyRequest().hasRole("ACTUATOR"))
                    .httpBasic(b -> {})
                    .authenticationManager(new ProviderManager(provider));
        } else {
            // dev/test/local - актуатор открыт для удобства локальной
            // разработки и automated test'ов. Изменение поведения
            // привязано к prod profile, а не к property-флагу - явная
            // safety boundary
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
