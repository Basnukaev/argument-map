package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.web.dto.CreateNodeRequest;

/**
 * IT для z-order endpoints. Проверяем что:
 * - bringToFront ставит z_index = max(z_index темы) + 1
 * - sendToBack ставит z_index = min(z_index темы) - 1
 * - permission check работает: чужой пользователь получает 403
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeZIndexIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                ownerId, "owner-" + ownerId, ownerId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, ?)",
                topicId, "Z-Order Topic", ownerId, "PRIVATE"
        );
    }

    @Test
    void bringToFront_setsToMaxPlus1() throws Exception {
        // создаём 2 узла - оба получают z_index=0 по DDL default
        UUID n1 = createNode("первый");
        UUID n2 = createNode("второй");

        // первый bringToFront для n1: max(0,0)+1 = 1
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/bring-to-front", n1)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(n1.toString()))
                .andExpect(jsonPath("$.zIndex").value(1));

        // теперь bringToFront для n2: max(0,1)+1 = 2
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/bring-to-front", n2)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(n2.toString()))
                .andExpect(jsonPath("$.zIndex").value(2));

        // повторный bringToFront n1: max(2,1)+1 = 3 (идемпотентность семантическая,
        // повторный вызов гарантирует "узел всё ещё сверху")
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/bring-to-front", n1)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zIndex").value(3));
    }

    @Test
    void sendToBack_setsToMinMinus1() throws Exception {
        UUID n1 = createNode("первый");
        UUID n2 = createNode("второй");

        // sendToBack n1: min(0,0) - 1 = -1
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/send-to-back", n1)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zIndex").value(-1));

        // sendToBack n2: min(-1, 0) - 1 = -2
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/send-to-back", n2)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zIndex").value(-2));
    }

    @Test
    void bringToFront_nonOwner_returns403() throws Exception {
        UUID nodeId = createNode("узел");

        // чужой user без membership - PRIVATE тема, write запрещён
        UUID strangerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                strangerId, "stranger-" + strangerId, strangerId + "@example.com"
        );

        // PRIVATE тема + чужой user без membership: assertCanWrite сначала
        // проверяет canRead (чтобы не leak'нуть существование private темы) -
        // возвращает forbidden-topic-access, а не -write
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/bring-to-front", nodeId)
                        .header("X-User-Id", strangerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void sendToBack_nonOwner_returns403() throws Exception {
        UUID nodeId = createNode("узел");

        UUID strangerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                strangerId, "stranger-" + strangerId, strangerId + "@example.com"
        );

        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/send-to-back", nodeId)
                        .header("X-User-Id", strangerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void bringToFront_nonExistentNode_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/nodes/{id}/z-order/bring-to-front", UUID.randomUUID())
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    private UUID createNode(String content) throws Exception {
        var req = new CreateNodeRequest(topicId, NodeType.CLAIM, content, null, null, null);
        String json = mockMvc.perform(post("/api/v1/nodes")
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
