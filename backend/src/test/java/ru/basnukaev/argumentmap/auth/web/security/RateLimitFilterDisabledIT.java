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
 * IT-проверка что при {@code auth.rate-limit.enabled=false} filter
 * выполняет no-op (даже 100 попыток подряд - 401, не 429). Это default
 * для dev/test/local profile - чтобы baseline 890+ tests не сломались.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "auth.rate-limit.enabled=false",
        "auth.rate-limit.login-attempts-per-minute=5"
})
class RateLimitFilterDisabledIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void disabledProperty_skipsRateLimitCompletely() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        // 20 попыток >> 5 лимит - но filter disabled, все 401
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.99")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
