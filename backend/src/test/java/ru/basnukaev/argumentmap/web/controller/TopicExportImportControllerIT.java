package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.web.dto.CreateTopicRequest;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto;
import ru.basnukaev.argumentmap.web.dto.TopicExportDto.TopicData;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicExportImportControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
    }

    @Test
    void exportTopic_returns200WithFilenameHeader() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}/export", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"topic-")))
                .andExpect(jsonPath("$.formatVersion").value("1.0"))
                .andExpect(jsonPath("$.topic.id").value(topicId.toString()))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.authorities").isArray());
    }

    @Test
    void exportTopic_notFound_returns404() throws Exception {
        // export теперь требует principal (@CurrentUser) - без X-User-Id
        // был бы 401 до handler'а. С валидным user'ом доходим до
        // assertCanRead → findById → TopicNotFound (404).
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/topics/{id}/export", missing)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("topic-not-found")));
    }

    @Test
    void exportTopic_privateTopicOfAnotherUser_returns403() throws Exception {
        // owner создаёт PRIVATE тему (default), другой user пытается
        // экспортнуть - раньше export шёл без проверки и сливал приватную
        // тему целиком. Теперь assertCanRead → 403 (ADR-043).
        UUID topicId = createTopicViaApi();

        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );

        mockMvc.perform(get("/api/v1/topics/{id}/export", topicId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void importJson_validPayload_returns201WithTopicId() throws Exception {
        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(UUID.randomUUID(), "Имп тема через JSON",
                        "desc", null, userId, Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/topics/import")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicId").exists())
                .andExpect(jsonPath("$.importedNodes").value(0))
                .andExpect(jsonPath("$.importedEdges").value(0))
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void importMultipart_validFile_returns201() throws Exception {
        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(UUID.randomUUID(), "Имп через файл",
                        null, null, userId, Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        byte[] bytes = objectMapper.writeValueAsBytes(dto);
        MockMultipartFile file = new MockMultipartFile(
                "file", "topic-export.json",
                MediaType.APPLICATION_JSON_VALUE, bytes);

        mockMvc.perform(multipart("/api/v1/topics/import")
                        .file(file)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicId").exists());
    }

    @Test
    void importJson_invalidFormatVersion_returns422() throws Exception {
        TopicExportDto dto = new TopicExportDto(
                "99.999", Instant.now(),
                new TopicData(UUID.randomUUID(), "Бад версия", null, null, userId, Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/topics/import")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("unsupported-format-version")))
                .andExpect(jsonPath("$.receivedVersion").value("99.999"))
                .andExpect(jsonPath("$.supportedVersions").isArray());
    }

    @Test
    void importJson_withoutUserHeader_returns401() throws Exception {
        // ADR-040 + b9da308: permitAll в dev/test, но @CurrentUser резолвер
        // при anonymous principal бросает InvalidTokenException → 401
        // invalid-token (frontend refresh-on-401 interceptor trigger)
        TopicExportDto dto = new TopicExportDto(
                "1.0", Instant.now(),
                new TopicData(UUID.randomUUID(), "T", null, null, userId, Instant.now()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/topics/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
    }

    private UUID createTopicViaApi() throws Exception {
        var req = new CreateTopicRequest("T", null, "Q?", null);
        String json = mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
