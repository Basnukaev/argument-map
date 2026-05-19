package ru.basnukaev.argumentmap.auth.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.web.dto.LoginRequest;
import ru.basnukaev.argumentmap.auth.web.dto.RegisterRequest;

/**
 * IT для {@link RateLimitFilter} (ADR-046). Проверяет полный flow:
 * limit allow → exceed → lockout 429 → expiry reset → whitelist bypass.
 *
 * <p>Property overrides включают rate-limit, ставят low limits для
 * скорости (5/min login, 3/min register, lockout 1 секунда чтобы
 * `lockout_expires_unblocks` не вистал на 15 минут - но реально
 * используем mutable clock fast-forward).
 *
 * <p>Whitelist в этих тестах ПУСТОЙ - чтобы MockMvc-запросы с
 * remoteAddr 127.0.0.1 не bypassили filter. Localhost whitelist
 * остаётся в prod default'е (см. RateLimitProperties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RateLimitFilterIT.MutableClockConfig.class})
@TestPropertySource(properties = {
        "auth.rate-limit.enabled=true",
        "auth.rate-limit.login-attempts-per-minute=5",
        "auth.rate-limit.register-attempts-per-minute=3",
        "auth.rate-limit.lockout-duration=PT15M",
        // Whitelist пустой - MockMvc remoteAddr=127.0.0.1 не bypass'ит фильтр
        "auth.rate-limit.whitelisted-ips="
})
class RateLimitFilterIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimitFilter rateLimitFilter;
    @Autowired private MutableClock mutableClock;

    @BeforeEach
    void resetState() {
        rateLimitFilter.resetState();
        mutableClock.reset();
    }

    @Test
    void login_underLimit_allowsAllAttempts() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong-pass");
        for (int i = 0; i < 4; i++) {
            // 401 invalid-credentials - rate limit не сработал
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.10")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void login_atLimit_returns429WithRetryAfter() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong-pass");
        // 5 первых попыток - 401 (limit = 5, attempts 1..5 проходят)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.20")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
        // 6-я попытка превышает лимит → 429
        var mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.20")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("too-many-requests")))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andReturn();
        String retryAfter = mvcResult.getResponse().getHeader("Retry-After");
        if (retryAfter == null || Long.parseLong(retryAfter) < 1) {
            throw new AssertionError("Retry-After должен быть >=1, got: " + retryAfter);
        }
    }

    @Test
    void register_separateCounter_fromLogin() throws Exception {
        // 5 попыток login - не должны consume лимит register (3/min)
        var loginReq = new LoginRequest("nobody@example.com", "wrong");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.30")
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isUnauthorized());
        }
        // 1-я register попытка с того же IP - allow (счётчик register пустой)
        var regReq = new RegisterRequest("brand-new@example.com", "newuser", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.30")
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_atLimit_returns429() throws Exception {
        // Лимит register = 3/min, после 3 успешных - 4-я 429
        for (int i = 0; i < 3; i++) {
            var req = new RegisterRequest("u" + i + "@example.com", "user" + i, "password1");
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.40")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }
        var req = new RegisterRequest("u4@example.com", "user4", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.40")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void lockout_persists_withinLockoutWindow() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        // Превысить лимит
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.50")
                            .content(objectMapper.writeValueAsString(req)));
        }
        // Продвинуть clock на 10 секунд - lockout 15 минут ещё держится
        mutableClock.advance(Duration.ofSeconds(10));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.50")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void lockout_expires_unblocksAfterDuration() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.60")
                            .content(objectMapper.writeValueAsString(req)));
        }
        // Fast-forward через весь lockout window (15 min + buffer)
        mutableClock.advance(Duration.ofMinutes(16));

        // Следующая попытка - allow (lockout expired, attempts reset)
        // Ожидаем 401 invalid-credentials, не 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.60")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void windowSlides_oldAttemptsEvicted() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        // 3 попытки в начале окна
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.70")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
        // Продвинуть на 65 сек (> 1 минута sliding window)
        mutableClock.advance(Duration.ofSeconds(65));
        // Старые 3 попытки evicted, новые 5 должны проходить
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.70")
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void differentIps_independentCounters() throws Exception {
        var req = new LoginRequest("nobody@example.com", "wrong");
        // IP A исчерпал лимит
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113.80")
                            .content(objectMapper.writeValueAsString(req)));
        }
        // IP A → 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.80")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());
        // IP B - первая попытка, allow
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "198.51.100.1")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Mutable clock для fast-forward через lockout без Thread.sleep.
     * Override default {@link Clock} bean из {@code AuthClockConfig}.
     */
    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        public Clock testClock() {
            return new MutableClock();
        }

        @Bean
        public MutableClock mutableClock(Clock clock) {
            return (MutableClock) clock;
        }
    }

    /**
     * Mutable Clock - inicializes на now() при создании, после reset
     * возвращается к начальной точке. advance(duration) сдвигает
     * наружу видимый "current instant".
     */
    static class MutableClock extends Clock {
        private final Instant base = Instant.parse("2026-05-19T10:00:00Z");
        private volatile Instant current = base;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        public void advance(Duration d) {
            current = current.plus(d);
        }

        public void reset() {
            current = base;
        }
    }
}
