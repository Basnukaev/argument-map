package ru.basnukaev.argumentmap.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
    void createTopic_withoutUserHeader_returns401_problemDetail() throws Exception {
        // ADR-040 + b9da308: /api/** permitAll в dev/test, но @CurrentUser
        // резолвер при anonymous principal бросает InvalidTokenException →
        // 401 invalid-token. Это correct behavior для frontend refresh-on-401
        // interceptor - прозрачный refresh цикла
        var req = new CreateTopicRequest("T", null, "Q?", null);

        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("invalid-token")))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void createTopic_withInvalidUserHeader_returns401() throws Exception {
        // ADR-040 + b9da308: невалидный UUID в X-User-Id → XUserIdFilter молча
        // пропускает, SecurityContext пуст → @CurrentUser резолвер бросает
        // InvalidTokenException → 401 invalid-token (см. test выше)
        var req = new CreateTopicRequest("T", null, "Q?", null);

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
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
    void listTopics_returnsPagedResponse() throws Exception {
        createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listTopics_includesNodeCountAndEdgeCount() throws Exception {
        createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                // только что созданная тема имеет 1 узел (корневой вопрос) и 0 рёбер
                .andExpect(jsonPath("$.items[0].nodeCount").value(1))
                .andExpect(jsonPath("$.items[0].edgeCount").value(0));
    }

    @Test
    void listTopics_paginated_returnsCorrectPage() throws Exception {
        for (int i = 0; i < 5; i++) {
            createTopicViaApi();
        }
        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrev").value(true));
    }

    @Test
    void listTopics_filterByVisibility_returnsOnlyMatching() throws Exception {
        createTopicViaApi(); // PRIVATE (default)
        // создаём PUBLIC тему этим же user'ом
        var publicReq = new CreateTopicRequest("Pub", null, "Q?", "PUBLIC");
        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publicReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .param("visibility", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].visibility").value("PUBLIC"));
    }

    @Test
    void listTopics_invalidVisibilityFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/topics")
                        .header("X-User-Id", userId.toString())
                        .param("visibility", "SUPER_SECRET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("illegal-argument")));
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
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(privateTopicId.toString()));
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

    /**
     * ADR-043 Amendment 3 (22.d) - POST /topics должен оставить
     * audit_log entry. Тест проверяет integration TopicService →
     * AuditLogService → audit_log таблица.
     */
    @Test
    void createTopic_writesAuditEntry() throws Exception {
        UUID topicId = createTopicViaApi();

        // 1 row TOPIC create + 1 row root NODE create = 2 entries
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE (entity_type = 'TOPIC' AND entity_id = ?) "
                        + "OR (parent_entity_type = 'TOPIC' AND parent_entity_id = ?)",
                Integer.class, topicId, topicId
        );
        assertThat(count).isEqualTo(2);

        // действие CREATE с правильным actor
        Integer topicCreateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log "
                        + "WHERE entity_type = 'TOPIC' AND entity_id = ? "
                        + "AND action = 'CREATE' AND actor_user_id = ?",
                Integer.class, topicId, userId
        );
        assertThat(topicCreateCount).isEqualTo(1);
    }
}
