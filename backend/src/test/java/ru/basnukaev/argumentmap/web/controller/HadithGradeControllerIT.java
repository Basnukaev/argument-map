package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ru.basnukaev.argumentmap.domain.Authority;
import ru.basnukaev.argumentmap.domain.HadithGradeValue;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.Source;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.repository.AuthorityRepository;
import ru.basnukaev.argumentmap.repository.SourceRepository;
import ru.basnukaev.argumentmap.web.dto.CreateHadithGradeRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateHadithGradeRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithGradeControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID otherUserId;
    private UUID hadithSourceId;
    private UUID bookSourceId;
    private UUID scholarId;

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

        Authority scholar = new Authority(UUID.randomUUID(), "Аль-Бухари",
                "Имам-мухаддис", "III век хиджры", null, null, Instant.now(),
                "Мухаммад ибн Исмаил аль-Бухари", 256);
        authorityRepository.save(scholar);
        scholarId = scholar.id();

        Source hadith = new Source(UUID.randomUUID(), SourceType.HADITH,
                "Хадис о намерениях", "Бухари 1", Reliability.SAHIH,
                null, null, null, Instant.now());
        sourceRepository.save(hadith);
        hadithSourceId = hadith.id();

        Source book = new Source(UUID.randomUUID(), SourceType.BOOK,
                "Книга", "Автор", null, null, null, null, Instant.now());
        sourceRepository.save(book);
        bookSourceId = book.id();
    }

    @Test
    void POST_validGrade_returns201() throws Exception {
        var req = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH,
                "Сахих аль-Бухари 1/1", "Согласовано");

        mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/sources/grades/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sourceId").value(hadithSourceId.toString()))
                .andExpect(jsonPath("$.scholarId").value(scholarId.toString()))
                .andExpect(jsonPath("$.grade").value("SAHIH"))
                .andExpect(jsonPath("$.gradeCitation").value("Сахих аль-Бухари 1/1"))
                .andExpect(jsonPath("$.comment").value("Согласовано"))
                .andExpect(jsonPath("$.createdBy").value(userId.toString()));
    }

    @Test
    void POST_nonHadithSource_returns400() throws Exception {
        var req = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH, null, null);

        mockMvc.perform(post("/api/v1/sources/{id}/grades", bookSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(containsString("invalid-hadith-grade")));
    }

    @Test
    void POST_duplicateScholar_returns409() throws Exception {
        var req = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH, null, null);

        mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(containsString("hadith-grade-duplicate")));
    }

    @Test
    void POST_missingScholar_returns400ValidationError() throws Exception {
        // scholarId = null - @NotNull
        String body = "{\"grade\":\"SAHIH\"}";

        mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_grades_returns200WithList() throws Exception {
        var req = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH,
                "1/1", "Базовая оценка");
        mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sources/{id}/grades", hadithSourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grade").value("SAHIH"))
                .andExpect(jsonPath("$[0].scholarName").value("Аль-Бухари"))
                .andExpect(jsonPath("$[0].scholarFullName").value("Мухаммад ибн Исмаил аль-Бухари"))
                .andExpect(jsonPath("$[0].scholarDeathYearHijri").value(256));
    }

    @Test
    void PATCH_grade_authoredByActor_updates() throws Exception {
        var createReq = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH, null, null);
        String created = mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn().getResponse().getContentAsString();
        UUID gradeId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        var updateReq = new UpdateHadithGradeRequest(HadithGradeValue.HASAN, "Новая ссылка", "Передумал");
        mockMvc.perform(patch("/api/v1/sources/grades/{id}", gradeId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value("HASAN"))
                .andExpect(jsonPath("$.gradeCitation").value("Новая ссылка"))
                .andExpect(jsonPath("$.comment").value("Передумал"));
    }

    @Test
    void PATCH_grade_nonAuthor_returns403() throws Exception {
        var createReq = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH, null, null);
        String created = mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn().getResponse().getContentAsString();
        UUID gradeId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        var updateReq = new UpdateHadithGradeRequest(HadithGradeValue.DAIF, null, null);
        mockMvc.perform(patch("/api/v1/sources/grades/{id}", gradeId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-hadith-grade-write")));
    }

    @Test
    void DELETE_grade_returns204() throws Exception {
        var createReq = new CreateHadithGradeRequest(scholarId, HadithGradeValue.SAHIH, null, null);
        String created = mockMvc.perform(post("/api/v1/sources/{id}/grades", hadithSourceId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn().getResponse().getContentAsString();
        UUID gradeId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(delete("/api/v1/sources/grades/{id}", gradeId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sources/{id}/grades", hadithSourceId))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void DELETE_grade_missing_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/sources/grades/{id}", UUID.randomUUID())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("hadith-grade-not-found")));
    }
}
