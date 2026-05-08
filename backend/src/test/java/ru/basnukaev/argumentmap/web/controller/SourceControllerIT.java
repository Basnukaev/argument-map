package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.SourceType;
import ru.basnukaev.argumentmap.web.dto.CreateSourceRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SourceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createSource_hadithWithReliabilityAndMetadata_returns201() throws Exception {
        var metadata = objectMapper.readTree("{\"collection\":\"bukhari\",\"book\":1,\"hadith\":4}");
        var req = new CreateSourceRequest(
                SourceType.HADITH, "Сахих аль-Бухари", "том 1, хадис 4",
                Reliability.SAHIH, null, metadata
        );

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/sources/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sourceType").value("HADITH"))
                .andExpect(jsonPath("$.reliability").value("SAHIH"))
                .andExpect(jsonPath("$.metadata.collection").value("bukhari"))
                .andExpect(jsonPath("$.metadata.book").value(1));
    }

    @Test
    void createSource_bookWithoutReliability_returns201() throws Exception {
        var req = new CreateSourceRequest(SourceType.BOOK, "Ихьйа улюм ад-дин", null, null, null, null);

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reliability").doesNotExist())
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }

    @Test
    void createSource_reliabilityForNonHadith_returns422() throws Exception {
        var req = new CreateSourceRequest(SourceType.BOOK, "title", null, Reliability.SAHIH, null, null);

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(containsString("invalid-source")))
                .andExpect(jsonPath("$.detail").value(containsString("HADITH")));
    }

    @Test
    void createSource_blankTitle_returns400() throws Exception {
        var req = new CreateSourceRequest(SourceType.URL, "  ", null, null, null, null);

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='title')]").exists());
    }

    @Test
    void getSource_existing_returns200() throws Exception {
        UUID id = createSource("Источник 1");

        mockMvc.perform(get("/api/v1/sources/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Источник 1"));
    }

    @Test
    void getSource_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/sources/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("source-not-found")));
    }

    @Test
    void listSources_returnsAll() throws Exception {
        createSource("a");
        createSource("b");

        mockMvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listSources_withQuery_filtersByTitle() throws Exception {
        createSource("Сахих аль-Бухари");
        createSource("Муснад Ахмада");

        mockMvc.perform(get("/api/v1/sources").param("q", "сахих"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Сахих аль-Бухари"));
    }

    @Test
    void deleteSource_existing_returns204() throws Exception {
        UUID id = createSource("x");

        mockMvc.perform(delete("/api/v1/sources/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sources/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSource_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/sources/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createSource(String title) throws Exception {
        var req = new CreateSourceRequest(SourceType.BOOK, title, null, null, null, null);
        String json = mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
