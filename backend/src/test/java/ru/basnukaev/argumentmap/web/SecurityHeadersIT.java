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
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * IT для cross-cutting HTTP security headers (audit findings #2).
 * Тестируем в default profile (local/test) - именно тут HSTS и CSP
 * выключены. Headers которые включены всегда (Spring default +
 * наш Referrer-Policy + Permissions-Policy) проверяем.
 *
 * <p>HSTS и CSP - prod-only поведение, тест-кейсы которые их
 * требуют были бы фактически тестом @Profile("prod") - не делаем,
 * unit-coverage SecurityConfig branch достаточно.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityHeadersIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anyEndpoint_setsReferrerPolicyHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void anyEndpoint_setsPermissionsPolicyHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Permissions-Policy",
                        Matchers.allOf(
                                Matchers.containsString("camera=()"),
                                Matchers.containsString("microphone=()"),
                                Matchers.containsString("geolocation=()"))));
    }

    @Test
    void anyEndpoint_setsXContentTypeOptionsNosniff() throws Exception {
        // Spring Security 6 default - не наш кастом, но регрессия словить хочется
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void anyEndpoint_setsXFrameOptionsDeny() throws Exception {
        // Spring Security 6 default. Защищает от clickjacking
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void devProfile_doesNotSetHstsHeader() throws Exception {
        // В test profile (local/dev/test) HSTS не включён - browser
        // игнорировал бы header на http:// origin и спамил DevTools
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void devProfile_doesNotSetCspHeader() throws Exception {
        // CSP - prod-only по тем же причинам что HSTS (Vite dev HMR
        // ws+unsafe-inline иначе ломаются). В тестовом profile CSP off
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }
}
