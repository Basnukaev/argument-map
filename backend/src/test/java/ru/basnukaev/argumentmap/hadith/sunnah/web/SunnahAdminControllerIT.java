package ru.basnukaev.argumentmap.hadith.sunnah.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.domain.UserRole;

/**
 * IT для {@link SunnahAdminController} (Phase 5 ETL шаг 2.d). Покрывает
 * ADMIN-only авторизацию и 503 при несконфигурированном источнике дампа
 * (в тест-профиле {@code sunnah.dump.enabled} не задан → reader-бина нет).
 * Happy-path импорта покрыт {@code SunnahImportServiceIT} (dual-container).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SunnahAdminControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("admin", UserRole.ADMIN);
        userId = insertUser("user", UserRole.USER);
    }

    @Test
    void import_as_admin_without_configured_source_returns_503() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", containsString("sunnah-dump-not-configured")));
    }

    @Test
    void import_as_non_admin_returns_403() throws Exception {
        // admin-check раньше source() — даже без сконфигурированного дампа → 403
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void list_collections_as_admin_without_source_returns_503() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/collections")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", containsString("sunnah-dump-not-configured")));
    }

    // ---- фазовые endpoints (ADR-052): browse / preview / single-import ----
    // Те же два гварда что у bulk-импорта: ADMIN-only (403 для USER) +
    // 503-gate несконфигурированного дампа (для ADMIN). Happy-path —
    // SunnahPhasedImportControllerIT (dual-container).

    @Test
    void browse_hadiths_as_non_admin_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/collections/bukhari/hadiths")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void browse_hadiths_as_admin_without_source_returns_503() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/collections/bukhari/hadiths")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", containsString("sunnah-dump-not-configured")));
    }

    @Test
    void preview_as_non_admin_returns_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/preview/bukhari/1")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void preview_as_admin_without_source_returns_503() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sunnah/preview/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", containsString("sunnah-dump-not-configured")));
    }

    @Test
    void single_import_as_non_admin_returns_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/1")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", containsString("forbidden-admin-only")));
    }

    @Test
    void single_import_as_admin_without_source_returns_503() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sunnah/import/bukhari/1")
                        .header("X-User-Id", adminId.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", containsString("sunnah-dump-not-configured")));
    }

    private UUID insertUser(String suffix, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com", role);
        return id;
    }
}
