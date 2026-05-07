package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        var req = new CreateTopicRequest("Мавлид это бид'а?", "разбор", "Допустимо ли?");

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
        var req = new CreateTopicRequest("T", null, "Q?");

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
        var req = new CreateTopicRequest("T", null, "Q?");

        mockMvc.perform(post("/api/v1/topics")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("UUID")));
    }

    @Test
    void createTopic_withBlankTitle_returns400_validationError() throws Exception {
        var req = new CreateTopicRequest("", null, "Q?");

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

        mockMvc.perform(get("/api/v1/topics/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("topic-not-found")))
                .andExpect(jsonPath("$.detail").value(containsString(missing.toString())));
    }

    @Test
    void getTopic_existing_returns200() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}", topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topicId.toString()));
    }

    @Test
    void listTopics_returnsArray() throws Exception {
        createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listTopics_includesNodeCountAndEdgeCount() throws Exception {
        createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                // только что созданная тема имеет 1 узел (корневой вопрос) и 0 рёбер
                .andExpect(jsonPath("$[0].nodeCount").value(1))
                .andExpect(jsonPath("$[0].edgeCount").value(0));
    }

    @Test
    void getOne_includesNodeCountAndEdgeCount() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}", topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeCount").value(1))
                .andExpect(jsonPath("$.edgeCount").value(0));
    }

    @Test
    void deleteTopic_existing_returns204() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(delete("/api/v1/topics/{id}", topicId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/topics/{id}", topicId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTopic_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/topics/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getGraph_returnsTopicNodesAndEdges() throws Exception {
        UUID topicId = createTopicViaApi();

        mockMvc.perform(get("/api/v1/topics/{id}/graph", topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic.id").value(topicId.toString()))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(0));
    }

    private UUID createTopicViaApi() throws Exception {
        var req = new CreateTopicRequest("T", null, "Q?");
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
