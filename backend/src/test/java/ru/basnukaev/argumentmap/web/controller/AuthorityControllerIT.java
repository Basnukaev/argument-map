package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.basnukaev.argumentmap.web.dto.CreateAuthorityRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorityControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAuthority_returns201() throws Exception {
        var metadata = objectMapper.readTree("{\"birth_year\":1263}");
        var req = new CreateAuthorityRequest(
                "Ибн Таймия", "Известный учёный", "XIII-XIV век", "ханбалитский", metadata, null
        );

        mockMvc.perform(post("/api/v1/authorities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Ибн Таймия"))
                .andExpect(jsonPath("$.madhab").value("ханбалитский"))
                .andExpect(jsonPath("$.metadata.birth_year").value(1263));
    }

    @Test
    void createAuthority_blankName_returns400() throws Exception {
        var req = new CreateAuthorityRequest("  ", null, null, null, null, null);

        mockMvc.perform(post("/api/v1/authorities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    void getAuthority_existing_returns200() throws Exception {
        UUID id = createAuthority("Имам Малик");

        mockMvc.perform(get("/api/v1/authorities/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Имам Малик"));
    }

    @Test
    void getAuthority_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/authorities/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(containsString("authority-not-found")));
    }

    @Test
    void listAuthorities_returnsAllAsPagedResponse() throws Exception {
        createAuthority("a");
        createAuthority("b");

        mockMvc.perform(get("/api/v1/authorities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listAuthorities_withQuery_filtersByName() throws Exception {
        createAuthority("Имам Малик");
        createAuthority("Имам Шафии");
        createAuthority("Ибн Хазм");

        mockMvc.perform(get("/api/v1/authorities").param("q", "имам"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listAuthorities_paginated_secondPage() throws Exception {
        for (int i = 0; i < 5; i++) {
            createAuthority("auth-" + i);
        }
        mockMvc.perform(get("/api/v1/authorities")
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
    void listAuthorities_filterByEra_returnsOnlyMatching() throws Exception {
        createAuthorityWithEra("Малик", "VIII век");
        createAuthorityWithEra("Шафии", "VIII век");
        createAuthorityWithEra("Ибн Таймия", "XIII-XIV век");

        mockMvc.perform(get("/api/v1/authorities").param("era", "VIII век"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private UUID createAuthorityWithEra(String name, String era) throws Exception {
        var req = new CreateAuthorityRequest(name, null, era, null, null, null);
        String json = mockMvc.perform(post("/api/v1/authorities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    @Test
    void deleteAuthority_existing_returns204() throws Exception {
        UUID id = createAuthority("x");

        mockMvc.perform(delete("/api/v1/authorities/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAuthority_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/authorities/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private UUID createAuthority(String name) throws Exception {
        var req = new CreateAuthorityRequest(name, null, null, null, null, null);
        String json = mockMvc.perform(post("/api/v1/authorities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
