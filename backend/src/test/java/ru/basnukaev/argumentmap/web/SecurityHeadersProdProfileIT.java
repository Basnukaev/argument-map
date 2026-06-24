package ru.basnukaev.argumentmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * IT для prod-profile HTTP security headers (HSTS + CSP) - regression net
 * к {@link SecurityHeadersIT} (default test-profile headers).
 *
 * <p>Code review round 5 #4 - {@link SecurityHeadersIT} проверял только
 * dev-profile поведение (HSTS off / CSP off). Если кто-то по ошибке
 * уберёт {@code if (!devOrTestProfile)} branch из {@link
 * ru.basnukaev.argumentmap.auth.web.security.SecurityConfig} - dev
 * тесты продолжат проходить, а prod в production задеплоится без
 * HSTS/CSP. Этот класс ловит такую регрессию.
 *
 * <p>Прогон под `@ActiveProfiles("prod")` требует non-placeholder JWT
 * secret 32+ chars (JwtService fail-fast в prod profile если содержит
 * "dev-only"). Override через TestPropertySource.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "auth.jwt.secret=test-prod-secret-32chars-or-more-for-hs256-validation",
        // ADR-048 ActuatorSecurityConfig fail-fast в prod profile если
        // actuator.security.username/password пусты. Тесты этого класса
        // про headers, не про actuator security - заглушаем placeholder
        "actuator.security.username=testactuator",
        "actuator.security.password=testpass",
        // P0-3: датасорс здесь из Testcontainers @ServiceConnection (localhost),
        // а DatasourceConfigValidator под prod-profile отверг бы localhost / упал
        // бы на неразрешённом ${SPRING_DATASOURCE_URL}. Гард покрыт своим
        // DatasourceConfigValidatorTest, тут отключаем его.
        "app.datasource.prod-guard=false"
})
class SecurityHeadersProdProfileIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prodProfile_setsHstsHeader_includesMaxAgeAndSubdomains() throws Exception {
        // HSTS в prod включается на 1 год (31_536_000 sec) + includeSubDomains.
        // preload=false осознанно - не подписываемся на HSTS preload list (требует
        // полного контроля над всеми поддоменами что неудобно для staging).
        //
        // .secure(true) обязателен - Spring Security HSTS writer эмитит header
        // только для request.isSecure()==true (HTTPS). MockMvc по умолчанию
        // secure=false. В реальном prod deployment requests приходят за HTTPS
        // LB (X-Forwarded-Proto=https) - server.forward-headers-strategy
        // настраивает isSecure() корректно.
        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security",
                        Matchers.allOf(
                                Matchers.containsString("max-age=31536000"),
                                Matchers.containsString("includeSubDomains"))));
    }

    @Test
    void prodProfile_setsCspHeader_includesDefaultSrcSelf() throws Exception {
        // CSP включён в prod (в dev был бы конфликт с Vite HMR ws+unsafe-eval).
        // Базовая регрессия - проверяем что header есть и содержит default-src
        // 'self' (наша primary defense против XSS из external sources).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.allOf(
                                Matchers.containsString("default-src 'self'"),
                                Matchers.containsString("frame-ancestors 'none'"))));
    }
}
