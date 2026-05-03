package ru.basnukaev.argumentmap.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDoc_returnsSpecWithAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths./api/v1/topics").exists())
                .andExpect(jsonPath("$.paths./api/v1/topics/{topicId}").exists())
                .andExpect(jsonPath("$.paths./api/v1/topics/{topicId}/graph").exists())
                .andExpect(jsonPath("$.paths./api/v1/nodes").exists())
                .andExpect(jsonPath("$.paths./api/v1/nodes/{nodeId}").exists())
                .andExpect(jsonPath("$.paths./api/v1/nodes/{nodeId}/revisions").exists())
                .andExpect(jsonPath("$.paths./api/v1/edges").exists())
                .andExpect(jsonPath("$.paths./api/v1/edges/{edgeId}").exists());
    }

    @Test
    void swaggerUi_redirectsOrServes() throws Exception {
        // springdoc-openapi обслуживает /swagger-ui.html → редирект на /swagger-ui/index.html
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void swaggerUi_indexPage_loads() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }
}
