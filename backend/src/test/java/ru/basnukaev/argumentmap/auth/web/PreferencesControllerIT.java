package ru.basnukaev.argumentmap.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

/**
 * IT для {@link PreferencesController} (Settings screen). Проверяет
 * REST контракт: GET/PUT/DELETE, валидация невалидных ключей →400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PreferencesControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "preftest-" + userId, userId + "@test.com"
        );
    }

    @Test
    void GET_returns200WithEmptyMapForNewUser() throws Exception {
        mockMvc.perform(get("/api/v1/preferences")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void PUT_bulkUpdate_returns200WithUpdatedMap() throws Exception {
        Map<String, Object> body = Map.of(
                "locale", "ar",
                "textSize", "large"
        );

        mockMvc.perform(put("/api/v1/preferences")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("ar"))
                .andExpect(jsonPath("$.textSize").value("large"));
    }

    @Test
    void PUT_invalidKey_returns400() throws Exception {
        Map<String, Object> body = Map.of("evilKey", "anything");

        mockMvc.perform(put("/api/v1/preferences")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PUT_invalidEnumValue_returns400() throws Exception {
        Map<String, Object> body = Map.of("locale", "klingon");

        mockMvc.perform(put("/api/v1/preferences")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PUT_singleKey_returns200() throws Exception {
        Map<String, Object> body = Map.of("value", "ar");

        mockMvc.perform(put("/api/v1/preferences/locale")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("ar"));
    }

    @Test
    void DELETE_existingKey_returns204() throws Exception {
        // Сначала создаём
        mockMvc.perform(put("/api/v1/preferences/locale")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"ar\"}"))
                .andExpect(status().isOk());

        // Удаляем
        mockMvc.perform(delete("/api/v1/preferences/locale")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        // GET не возвращает удалённый ключ
        mockMvc.perform(get("/api/v1/preferences")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").doesNotExist());
    }
}
