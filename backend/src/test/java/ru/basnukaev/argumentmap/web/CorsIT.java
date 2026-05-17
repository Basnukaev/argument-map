package ru.basnukaev.argumentmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorsIT {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_allowedOrigin_returnsCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/topics")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, X-User-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        Matchers.allOf(
                                Matchers.containsString("POST"),
                                Matchers.containsString("PATCH"),
                                Matchers.containsString("DELETE"))))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        Matchers.allOf(
                                Matchers.containsString("Content-Type"),
                                Matchers.containsString("X-User-Id"))));
    }

    @Test
    void preflight_disallowedOrigin_returnsForbidden() throws Exception {
        mockMvc.perform(options("/api/v1/topics")
                        .header("Origin", DISALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void simpleRequest_allowedOrigin_includesAllowOriginHeader() throws Exception {
        // ADR-043: GET /api/v1/topics требует @CurrentUser (visibility check).
        // Тест CORS - нужен валидный principal через X-User-Id (dev/test fallback)
        mockMvc.perform(get("/api/v1/topics")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void simpleRequest_withoutOrigin_worksWithoutCorsHeaders() throws Exception {
        // Same-origin / curl без Origin не получает CORS-заголовков и работает как обычно
        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
