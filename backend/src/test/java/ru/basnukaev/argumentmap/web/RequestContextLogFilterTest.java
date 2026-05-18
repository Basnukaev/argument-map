package ru.basnukaev.argumentmap.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.ServletException;
import ru.basnukaev.argumentmap.auth.domain.AuthenticatedUser;

class RequestContextLogFilterTest {

    private final RequestContextLogFilter filter = new RequestContextLogFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void doFilterInternal_setsRequestIdInMdcAndHeader_andClearsAfter() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/topics");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        // Header передан клиенту - X-Request-Id valid UUID
        String headerId = resp.getHeader("X-Request-Id");
        assertThat(headerId).isNotNull();
        UUID.fromString(headerId); // не бросает - валидный UUID

        // MDC очищено после finally блока
        assertThat(MDC.get(RequestContextLogFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestContextLogFilter.MDC_USER_ID)).isNull();
    }

    @Test
    void doFilterInternal_withAuthenticatedPrincipal_populatesUserIdInMdc() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "u1", "u@e.com", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, java.util.List.of())
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/topics");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // capture MDC inside filter chain (chain runs while MDC is set)
        var capturedUserId = new String[1];
        var capturedRequestId = new String[1];
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req2,
                                   jakarta.servlet.http.HttpServletResponse resp2) {
                capturedUserId[0] = MDC.get(RequestContextLogFilter.MDC_USER_ID);
                capturedRequestId[0] = MDC.get(RequestContextLogFilter.MDC_REQUEST_ID);
            }
        });

        filter.doFilterInternal(req, resp, chain);

        assertThat(capturedUserId[0]).isEqualTo(userId.toString());
        assertThat(capturedRequestId[0]).isNotBlank();
        // После finally - очищено
        assertThat(MDC.get(RequestContextLogFilter.MDC_USER_ID)).isNull();
    }

    @Test
    void doFilterInternal_noPrincipal_doesNotSetUserId() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/topics");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        var captured = new String[1];
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req2,
                                   jakarta.servlet.http.HttpServletResponse resp2) {
                captured[0] = MDC.get(RequestContextLogFilter.MDC_USER_ID);
            }
        });

        filter.doFilterInternal(req, resp, chain);

        // userId не выставлен - SecurityContext empty
        assertThat(captured[0]).isNull();
    }
}
