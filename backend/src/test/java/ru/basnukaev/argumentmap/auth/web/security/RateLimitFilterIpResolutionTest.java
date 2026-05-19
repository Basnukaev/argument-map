package ru.basnukaev.argumentmap.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit-тесты IP resolution и normalization. Покрывают edge cases которые
 * сложно достать через MockMvc (IPv6 brackets, X-Forwarded-For chains,
 * port suffix sanitization). Без Spring context - чистые методы.
 */
class RateLimitFilterIpResolutionTest {

    @Test
    void normalizeIp_strpsPort_fromIpv4() {
        assertThat(RateLimitFilter.normalizeIp("203.0.113.5:54321"))
                .isEqualTo("203.0.113.5");
    }

    @Test
    void normalizeIp_keepsRawIpv4_withoutPort() {
        assertThat(RateLimitFilter.normalizeIp("203.0.113.5"))
                .isEqualTo("203.0.113.5");
    }

    @Test
    void normalizeIp_stripsPort_fromIpv6BracketNotation() {
        assertThat(RateLimitFilter.normalizeIp("[2001:db8::1]:54321"))
                .isEqualTo("2001:db8::1");
    }

    @Test
    void normalizeIp_keepsIpv6_withoutBrackets() {
        // IPv6 без brackets - несколько двоеточий, не считаем последний как порт
        assertThat(RateLimitFilter.normalizeIp("2001:db8::1"))
                .isEqualTo("2001:db8::1");
        assertThat(RateLimitFilter.normalizeIp("::1"))
                .isEqualTo("::1");
    }

    @Test
    void normalizeIp_handlesEmptyOrNull() {
        assertThat(RateLimitFilter.normalizeIp(null)).isEqualTo("");
        assertThat(RateLimitFilter.normalizeIp("")).isEqualTo("");
    }

    @Test
    void resolveClientIp_preferXForwardedFor_firstValue() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1, 192.168.1.1");
        when(req.getHeader("X-Real-IP")).thenReturn("ignored");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(RateLimitFilter.resolveClientIp(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void resolveClientIp_fallsBackToXRealIp_whenNoXff() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn("198.51.100.10");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(RateLimitFilter.resolveClientIp(req)).isEqualTo("198.51.100.10");
    }

    @Test
    void resolveClientIp_fallsBackToRemoteAddr_whenNoHeaders() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(RateLimitFilter.resolveClientIp(req)).isEqualTo("127.0.0.1");
    }

    @Test
    void resolveClientIp_ignoresBlankXForwardedFor() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(req.getHeader("X-Real-IP")).thenReturn("198.51.100.10");
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(RateLimitFilter.resolveClientIp(req)).isEqualTo("198.51.100.10");
    }
}
