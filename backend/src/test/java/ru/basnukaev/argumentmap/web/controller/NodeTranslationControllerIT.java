package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.service.NodeService;
import ru.basnukaev.argumentmap.web.dto.CreateNodeTranslationRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateNodeTranslationRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeTranslationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;
    private UUID topicId;
    private UUID nodeId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by, visibility) VALUES (?, ?, ?, 'PRIVATE')",
                topicId, "T", userId
        );
        Node n = nodeService.createNode(topicId, NodeType.EVIDENCE, "إنما الأعمال", "ar", userId);
        nodeId = n.id();
    }

    @Test
    void POST_validTranslation_returns201() throws Exception {
        var req = new CreateNodeTranslationRequest("Кулиев", "ru", "Деяния оцениваются по намерениям", false);

        mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/nodes/translations/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.translatorName").value("Кулиев"))
                .andExpect(jsonPath("$.language").value("ru"))
                .andExpect(jsonPath("$.body").value("Деяния оцениваются по намерениям"))
                // первый перевод узла всегда default несмотря на false в запросе
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void POST_anonymousTranslator_returns201() throws Exception {
        var req = new CreateNodeTranslationRequest(null, "en", "By intentions", false);

        mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.translatorName").isEmpty());
    }

    @Test
    void POST_duplicate_returns409() throws Exception {
        var req = new CreateNodeTranslationRequest("Кулиев", "ru", "тест", false);

        mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(containsString("node-translation-duplicate")));
    }

    @Test
    void POST_invalidLanguage_returns400() throws Exception {
        // language=fr - запрещено @Pattern
        String body = "{\"translatorName\":\"X\",\"language\":\"fr\",\"body\":\"text\"}";

        mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_nonOwner_returns403() throws Exception {
        var req = new CreateNodeTranslationRequest("Кулиев", "ru", "перевод", false);

        mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void GET_translations_returnsListSortedDefaultFirst() throws Exception {
        UUID firstId = createTranslation("A", "ru", "первый", false);
        UUID secondId = createTranslation("B", "en", "second", false);
        // первый при создании сразу стал default. Делаем second default через setDefault action
        mockMvc.perform(post("/api/v1/nodes/translations/{id}/default", secondId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondId.toString()))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].id").value(firstId.toString()))
                .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test
    void PATCH_translation_updates() throws Exception {
        UUID translationId = createTranslation("Старый", "ru", "тест", false);

        var updateReq = new UpdateNodeTranslationRequest("Новый", "обновлённый текст");
        mockMvc.perform(patch("/api/v1/nodes/translations/{id}", translationId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translatorName").value("Новый"))
                .andExpect(jsonPath("$.body").value("обновлённый текст"));
    }

    @Test
    void POST_setDefault_atomicSwap() throws Exception {
        UUID firstId = createTranslation("A", "ru", "первый", false);
        UUID secondId = createTranslation("B", "en", "second", false);
        // первый - default (первый автоматом)
        mockMvc.perform(post("/api/v1/nodes/translations/{id}/default", secondId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondId.toString()))
                .andExpect(jsonPath("$.isDefault").value(true));

        // verify через GET что first потерял default
        mockMvc.perform(get("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(jsonPath("$[?(@.id=='" + firstId + "')].isDefault").value(false))
                .andExpect(jsonPath("$[?(@.id=='" + secondId + "')].isDefault").value(true));
    }

    @Test
    void DELETE_translation_returns204() throws Exception {
        UUID translationId = createTranslation("Кулиев", "ru", "тест", false);

        mockMvc.perform(delete("/api/v1/nodes/translations/{id}", translationId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void DELETE_translationMissing_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/nodes/translations/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-translation-not-found")));
    }

    private UUID createTranslation(String translator, String lang, String body, boolean isDefault) throws Exception {
        var req = new CreateNodeTranslationRequest(translator, lang, body, isDefault);
        String json = mockMvc.perform(post("/api/v1/nodes/{id}/translations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
