package ru.basnukaev.argumentmap.auth.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ru.basnukaev.argumentmap.auth.web.dto.ChangeRoleRequest;

/**
 * IT для {@link UserController} - Phase A.4 PATCH /api/v1/users/{id}/role.
 * Vision 49d Section 2.4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UserControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'ADMIN')",
                adminId, "admin-" + adminId, adminId + "@test.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role) VALUES (?, ?, ?, 'USER')",
                userId, "user-" + userId, userId + "@test.com"
        );
    }

    @Test
    void PATCH_byAdmin_promotesUserToScholar() throws Exception {
        var req = new ChangeRoleRequest("SCHOLAR");

        mockMvc.perform(patch("/api/v1/users/{id}/role", userId)
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("SCHOLAR"));
    }

    @Test
    void PATCH_byUserSelf_returns403_forbiddenAdminOnly() throws Exception {
        var req = new ChangeRoleRequest("SCHOLAR");

        mockMvc.perform(patch("/api/v1/users/{id}/role", userId)
                        .header("X-User-Id", userId.toString())  // не admin!
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-admin-only")));
    }

    @Test
    void PATCH_adminSelfDowngrade_returns400() throws Exception {
        // ADMIN не может downgrade себя в non-ADMIN (lockout protection)
        var req = new ChangeRoleRequest("USER");

        mockMvc.perform(patch("/api/v1/users/{id}/role", adminId)
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_invalidRole_returns400() throws Exception {
        var req = new ChangeRoleRequest("MODERATOR");  // не в whitelist

        mockMvc.perform(patch("/api/v1/users/{id}/role", userId)
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_emptyBody_returns400() throws Exception {
        String body = "{\"newRole\":\"\"}";

        mockMvc.perform(patch("/api/v1/users/{id}/role", userId)
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_sameRole_returns200_noChange() throws Exception {
        // current = USER, new = USER → no UPDATE, returns 200
        var req = new ChangeRoleRequest("USER");

        mockMvc.perform(patch("/api/v1/users/{id}/role", userId)
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void PATCH_nonExistentUser_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        var req = new ChangeRoleRequest("STUDENT");

        mockMvc.perform(patch("/api/v1/users/{id}/role", ghost)
                        .header("X-User-Id", adminId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
