package ru.basnukaev.argumentmap.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.time.Instant;
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
import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.web.dto.AttachSourceRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NodeSourceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID topicId;
    private UUID nodeId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com"
        );
        topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO topics (id, title, created_by) VALUES (?, ?, ?)",
                topicId, "T", userId
        );
        nodeId = insertNode();
        sourceId = insertSource();
    }

    @Test
    void attachSource_returns201_andLinkPersisted() throws Exception {
        var req = new AttachSourceRequest(sourceId, "точная цитата", "контекст", "стр. 42");

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.sourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.quote").value("точная цитата"));
    }

    @Test
    void attachSource_whenNodeMissing_returns404() throws Exception {
        var req = new AttachSourceRequest(sourceId, null, null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("node-not-found")));
    }

    @Test
    void attachSource_whenSourceMissing_returns404() throws Exception {
        var req = new AttachSourceRequest(UUID.randomUUID(), null, null, null);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("source-not-found")));
    }

    @Test
    void listNodeSources_returnsAttachments() throws Exception {
        attach(sourceId, "q1", "c1");
        UUID source2 = insertSource();
        attach(source2, "q2", "c2");

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listNodeSources_whenNodeMissing_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listNodeSources_hadithBridge_returnsHadithRef_andNullForPlainSource() throws Exception {
        // хадис-опора: collection + hadith + primary matn, прикреплён через #2.A
        UUID collectionId = insertCollection();
        UUID hadithId = insertHadith(collectionId, 1, "CANONICAL");
        insertPrimaryMatn(hadithId, "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ");
        attachHadith(hadithId);

        // обычная (не-хадис) опора на том же узле
        attach(sourceId, "q-plain", "c-plain");

        var listResult = mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        var arr = objectMapper.readTree(listResult.getResponse().getContentAsString());

        // строку-хадис ищем по наличию non-null hadith; plain — по sourceId
        var hadithRow = findRow(arr, n -> !n.path("hadith").isNull() && n.path("hadith").isObject());
        var plainRow = findRow(arr, n -> sourceId.toString().equals(n.path("sourceId").asText()));

        var hadith = hadithRow.get("hadith");
        assertThat(hadith.get("hadithId").asText()).isEqualTo(hadithId.toString());
        assertThat(hadith.get("primaryNumber").asInt()).isEqualTo(1);
        assertThat(hadith.get("collectionName").asText()).isEqualTo("Сахих аль-Бухари");
        assertThat(hadith.get("previewMatn").asText()).contains("الأَعْمَالُ");
        assertThat(hadith.get("status").asText()).isEqualTo("CANONICAL");

        // обычная опора: hadith == null
        assertThat(plainRow.path("hadith").isNull()).isTrue();
    }

    private static com.fasterxml.jackson.databind.JsonNode findRow(
            com.fasterxml.jackson.databind.JsonNode arr,
            java.util.function.Predicate<com.fasterxml.jackson.databind.JsonNode> match) {
        for (var n : arr) {
            if (match.test(n)) {
                return n;
            }
        }
        throw new AssertionError("строка не найдена в ответе: " + arr);
    }

    @Test
    void detachSource_returns204() throws Exception {
        attach(sourceId, null, null);

        // Миграция 25 (FK variant A): DELETE по surrogate nodeSourceId
        // вместо (nodeId, sourceId) pair - находим id через GET /sources
        var listResult = mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = listResult.getResponse().getContentAsString();
        var node = objectMapper.readTree(responseBody);
        UUID nodeSourceId = UUID.fromString(node.get(0).get("id").asText());

        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/sources/{nodeSourceId}", nodeId, nodeSourceId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detachSource_whenNotAttached_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/sources/{nodeSourceId}", nodeId, UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    private void attach(UUID source, String quote, String context) throws Exception {
        var req = new AttachSourceRequest(source, quote, context, null);
        mockMvc.perform(post("/api/v1/nodes/{nodeId}/sources", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    /** Прикрепляет хадис через #2.A POST — создаёт мост Source + node_source. */
    private void attachHadith(UUID hadithId) throws Exception {
        mockMvc.perform(post("/api/v1/nodes/{nodeId}/hadith-citations", nodeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hadithId\":\"" + hadithId + "\"}"))
                .andExpect(status().isCreated());
    }

    private UUID insertCollection() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO hd_collections (id, slug, name_ar, name_en, name_ru, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "coll-" + id, "صحيح البخاري", "Sahih al-Bukhari",
                "Сахих аль-Бухари", odt(Instant.now())
        );
        return id;
    }

    private UUID insertHadith(UUID collectionId, int primaryNumber, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO hd_hadiths (id, collection_id, primary_number, normalized_matn, "
                        + "status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, collectionId, primaryNumber, "انما الاعمال بالنيات",
                status, odt(Instant.now())
        );
        return id;
    }

    private void insertPrimaryMatn(UUID hadithId, String textAr) {
        // text_ar_normalized NOT NULL (миграция 55) — кладём упрощённую версию
        jdbcTemplate.update(
                "INSERT INTO hd_matns (id, hadith_id, text_ar, text_ar_normalized, "
                        + "is_primary, created_at) VALUES (?, ?, ?, ?, true, ?)",
                UUID.randomUUID(), hadithId, textAr, "انما الاعمال بالنيات", odt(Instant.now())
        );
    }

    private UUID insertNode() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO nodes (id, topic_id, node_type, content, status, "
                        + "created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, topicId, NodeType.CLAIM.name(), "c", NodeStatus.UNVERIFIED.name(), userId, odt(now), odt(now)
        );
        return id;
    }

    private UUID insertSource() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sources (id, source_type, title) VALUES (?, ?, ?)",
                id, SourceType.BOOK.name(), "title-" + id
        );
        return id;
    }
}
