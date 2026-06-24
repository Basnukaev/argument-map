package ru.basnukaev.argumentmap.auth.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * IT для prod-profile actuator security (ADR-048). Покрывает три кейса:
 *
 * <ul>
 *   <li>{@code /actuator/health} и {@code /actuator/info} - public
 *       (LB liveness/readiness + CI/CD deploy verification)
 *   <li>{@code /actuator/circuitbreakers} (и любые другие) без auth - 401
 *   <li>{@code /actuator/circuitbreakers} с правильным Basic auth - 200
 *   <li>Basic auth с неверным паролем - 401
 * </ul>
 *
 * <p>Прогон под {@code @ActiveProfiles("prod")} требует non-placeholder
 * JWT secret 32+ chars (JwtService fail-fast в prod) и непустые
 * {@code actuator.security.*} (ActuatorSecurityConfig fail-fast).
 * Override через TestPropertySource.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "auth.jwt.secret=test-prod-secret-32chars-or-more-for-hs256-validation",
        "actuator.security.username=testactuator",
        "actuator.security.password=testpass",
        // P0-3: датасорс из Testcontainers @ServiceConnection (localhost) под
        // prod-profile споткнулся бы о DatasourceConfigValidator. Гард покрыт
        // отдельным DatasourceConfigValidatorTest - здесь отключаем.
        "app.datasource.prod-guard=false"
})
class ActuatorSecurityProdProfileIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String BASIC_AUTH_HEADER =
            "Basic " + Base64.getEncoder().encodeToString(
                    "testactuator:testpass".getBytes(StandardCharsets.UTF_8));

    @Test
    void prodProfile_health_publicAccess() throws Exception {
        // LB liveness/readiness - должен работать без auth даже в prod.
        // Иначе load balancer не сможет проверить health → false-negative
        // failures, instance уходит из ротации
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void prodProfile_info_publicAccess() throws Exception {
        // /actuator/info - используется CI/CD для verify deploy
        // (какая версия задеплоена). Public OK - info не leak'ает
        // секреты (только metadata типа git commit hash)
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void prodProfile_circuitbreakers_withoutAuth_returns401() throws Exception {
        // Без Authorization - 401. Защита от reconnaissance leak -
        // /circuitbreakers содержит имена всех instance, % failures,
        // current state - reveals topology backend'а
        mockMvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prodProfile_circuitbreakers_withBasicAuth_returns200() throws Exception {
        // С корректным basic auth - доступ открыт. Monitoring tools
        // (Prometheus scraper, k8s sidecar, datadog agent) используют
        // basic auth - стандартный механизм
        mockMvc.perform(get("/actuator/circuitbreakers")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_HEADER))
                .andExpect(status().isOk());
    }

    @Test
    void prodProfile_circuitbreakers_withWrongPassword_returns401() throws Exception {
        // Wrong credentials - 401 (не 403). Spring Security возвращает
        // 401 при failed basic auth challenge - триггерит retry на
        // monitoring stack стороне (basic auth retries are safe -
        // no state on server)
        String wrongAuth = "Basic " + Base64.getEncoder().encodeToString(
                "testactuator:wrongpass".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/actuator/circuitbreakers")
                        .header(HttpHeaders.AUTHORIZATION, wrongAuth))
                .andExpect(status().isUnauthorized());
    }
}
