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

    @Test
    void mutatingEndpoint_exposesXUserIdHeader_notQueryParam() throws Exception {
        // POST /api/v1/topics использует @CurrentUser - проверяем что
        // OperationCustomizer переписал query.userId на header X-User-Id.
        // После ADR-040 header стал optional (Bearer JWT - основной путь,
        // X-User-Id - dev/test fallback)
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // в параметрах операции есть header X-User-Id с типом uuid
                .andExpect(jsonPath(
                        "$.paths./api/v1/topics.post.parameters[?(@.name=='X-User-Id' && @.in=='header')].required"
                ).value(false))
                .andExpect(jsonPath(
                        "$.paths./api/v1/topics.post.parameters[?(@.name=='X-User-Id' && @.in=='header')].schema.format"
                ).value("uuid"))
                // и нет query userId
                .andExpect(jsonPath(
                        "$.paths./api/v1/topics.post.parameters[?(@.name=='userId' && @.in=='query')]"
                ).isEmpty());
    }

    @Test
    void multipleMutatingEndpoints_haveXUserIdHeader() throws Exception {
        // PATCH /api/v1/edges/{edgeId} тоже использует @CurrentUser - проверяем
        // что customizer применяется ко всем @CurrentUser операциям
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths./api/v1/nodes.post.parameters[?(@.name=='X-User-Id' && @.in=='header')].required"
                ).value(false))
                .andExpect(jsonPath(
                        "$.paths./api/v1/edges.post.parameters[?(@.name=='X-User-Id' && @.in=='header')].required"
                ).value(false));
    }
}
