package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import ru.basnukaev.argumentmap.web.dto.CreateTopicRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void createTopic_returns201_withLocationAndBody() throws Exception {
        var req = new CreateTopicRequest("Мавлид это бид'а?", "разбор", "Допустимо ли?", null);

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/topics/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Мавлид это бид'а?"))
                .andExpect(jsonPath("$.rootNodeId").exists())
                .andExpect(jsonPath("$.createdBy").value(userId.toString()));
    }

    @Test
    void createTopic_withoutUserHeader_returns400_problemDetail() throws Exception {
        // ADR-040 (dev/test profile): /api/** permitAll, но @CurrentUser
        // требует principal в SecurityContext - его нет без X-User-Id
        // или Bearer JWT → MissingUserHeaderException 400. В prod profile
        // Spring Security вернёт 401 раньше (без permitAll branch)
        var req = new CreateTopicRequest("T", null, "Q?", null);

        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("missing-user-header")))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createTopic_withInvalidUserHeader_returns400() throws Exception {
        // ADR-040: невалидный UUID в X-User-Id → XUserIdFilter молча
        // пропускает, SecurityContext пуст → @CurrentUser резолвер
        // бросает MissingUserHeaderException → 400 (см. test выше)
        var req = new CreateTopicRequest("T", null, "Q?", null);

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("missing-user-header")));
    }

    @Test
    void createTopic_withBlankTitle_returns400_validationError() throws Exception {
        var req = new CreateTopicRequest("", null, "Q?", null);

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("validation")))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field=='title')]").exists());
    }

    @Test
    void getTopic_whenNotFound_returns404_problemDetail() throws Exception {
        UUID missing = UUID.randomUUID();

        // ADR-043: PermissionService сначала пытается прочитать тему -
        // если нет, бросает TopicNotFoundException → 404. Если бы тема
        // была - была бы проверка visibility
        mockMvc.perform(get("/api/v1/topics/{id}", missing)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("topic-not-found")))
                .andExpect(jsonPath("$.detail").value(containsString(missing.toString())));
    }

    @Test
    void getTopic_existing_returns200() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topicId.toString()));
    }

    @Test
    void listTopics_returnsArray() throws Exception {
        createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listTopics_includesNodeCountAndEdgeCount() throws Exception {
        createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                // только что созданная тема имеет 1 узел (корневой вопрос) и 0 рёбер
                .andExpect(jsonPath("$[0].nodeCount").value(1))
                .andExpect(jsonPath("$[0].edgeCount").value(0));
    }

    @Test
    void getOne_includesNodeCountAndEdgeCount() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeCount").value(1))
                .andExpect(jsonPath("$.edgeCount").value(0));
    }

    @Test
    void deleteTopic_existing_returns204() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(delete("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTopic_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/topics/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGraph_returnsTopicNodesAndEdges() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}/graph", topicId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic.id").value(topicId.toString()))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(0));
    }

    // ---- ADR-043: visibility ----

    @Test
    void POST_topic_withVisibility_setsCorrectly() throws Exception {
        var req = new CreateTopicRequest("T", null, "Q?", "PUBLIC");

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
    }

    @Test
    void POST_topic_invalidVisibility_returns400() throws Exception {
        String json = "{\"title\":\"T\",\"rootQuestion\":\"Q?\",\"visibility\":\"SUPER_SECRET\"}";

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_topic_PRIVATE_byNonOwner_returns403() throws Exception {
        UUID topicId = createTopicViaApi();
        // другой user пытается прочитать PRIVATE тему
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );

        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void GET_topics_returnsOnlyVisible() throws Exception {
        UUID privateTopicId = createTopicViaApi(); // default PRIVATE

        // создаём другого user и его private тему
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );
        var otherReq = new CreateTopicRequest("Other private", null, "Q?", "PRIVATE");
        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherReq)))
                .andExpect(status().isCreated());

        // owner видит только свою тему - не чужую PRIVATE
        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(privateTopicId.toString()));
    }

    @Test
    void DELETE_topic_byNonOwner_returns403() throws Exception {
        UUID topicId = createTopicViaApi();
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                otherUserId, "other-" + otherUserId, otherUserId + "@example.com"
        );

        // меняем visibility на PUBLIC чтобы otherUser мог прочитать
        // (иначе будет 403 access, а мы хотим протестировать write-deny)
        var visReq = new ru.basnukaev.argumentmap.web.dto.UpdateTopicVisibilityRequest("PUBLIC");
        mockMvc.perform(patch("/api/v1/topics/{id}/visibility", topicId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(visReq)))
                .andExpect(status().isOk());

        // не-owner пытается удалить PUBLIC тему - 403
        mockMvc.perform(delete("/api/v1/topics/{id}", topicId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-write")));
    }

    private UUID createTopicViaApi() throws Exception {
        var req = new CreateTopicRequest("T", null, "Q?", null);
        String json = mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idValue = objectMapper.readTree(json).get("id").asText();
        return UUID.fromString(idValue);
    }
}
