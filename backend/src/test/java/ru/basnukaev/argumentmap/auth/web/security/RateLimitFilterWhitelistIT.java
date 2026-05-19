package ru.basnukaev.argumentmap.auth.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.web.dto.LoginRequest;

/**
 * IT для whitelist bypass. IP в {@code whitelisted-ips} не считаются -
 * unlimited attempts. CI / smoke / health probes используют этот mechanism.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "auth.rate-limit.enabled=true",
        "auth.rate-limit.login-attempts-per-minute=3",
        "auth.rate-limit.lockout-duration=PT15M",
        "auth.rate-limit.whitelisted-ips=192.0.2.250,203.0.113.250"
})
class RateLimitFilterWhitelistIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void whitelistedIp_unlimited() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        // 10 попыток c whitelisted IP >> 3 лимит - все 401, не 429
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "192.0.2.250")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void nonWhitelistedIp_hitLimit() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        // 3 попытки allow → 4-я 429
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "198.51.100.5")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "198.51.100.5")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());
    }
}
