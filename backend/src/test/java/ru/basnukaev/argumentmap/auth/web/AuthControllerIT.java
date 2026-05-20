package ru.basnukaev.argumentmap.auth.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.auth.web.dto.LoginRequest;
import ru.basnukaev.argumentmap.auth.web.dto.RegisterRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void POST_register_validInput_returns201WithTokenAndCookie() throws Exception {
        var req = new RegisterRequest("alice@example.com", "alice1", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.username").value("alice1"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void POST_register_invalidEmail_returns400() throws Exception {
        var req = new RegisterRequest("not-an-email", "user1", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void POST_register_shortPassword_returns400() throws Exception {
        var req = new RegisterRequest("user@example.com", "user1", "short");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_register_duplicateEmail_returns409() throws Exception {
        var first = new RegisterRequest("dupe@example.com", "userA", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        var second = new RegisterRequest("dupe@example.com", "userB", "password2");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(containsString("email-already-taken")));
    }

    @Test
    void POST_login_validCredentials_returns200WithTokenAndCookie() throws Exception {
        registerUser("login@example.com", "loginuser", "password1");

        var req = new LoginRequest("login@example.com", "password1");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void POST_login_wrongPassword_returns401() throws Exception {
        registerUser("wrongpw@example.com", "wrongpwuser", "password1");

        var req = new LoginRequest("wrongpw@example.com", "wrongone");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("invalid-credentials")));
    }

    @Test
    void POST_login_unknownEmail_returns401() throws Exception {
        var req = new LoginRequest("nobody@example.com", "password1");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GET_me_withValidBearer_returns200() throws Exception {
        registerUser("me@example.com", "meuser", "password1");
        String token = login("me@example.com", "password1");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.username").value("meuser"));
    }

    @Test
    void GET_me_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void GET_me_invalidBearer_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void POST_refresh_validCookie_returnsNewAccessToken_andNewRefresh() throws Exception {
        // ADR-047: single-use rotation. Set-Cookie должна содержать
        // новое значение refresh, не то же самое что input
        registerUser("refresh@example.com", "refreshuser", "password1");
        String oldRefresh = loginAndGetRefreshCookie("refresh@example.com", "password1");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("refresh@example.com"))
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        Cookie newCookie = result.getResponse().getCookie("refresh_token");
        org.junit.jupiter.api.Assertions.assertNotNull(newCookie);
        org.junit.jupiter.api.Assertions.assertNotEquals(oldRefresh, newCookie.getValue(),
                "ADR-047: новый refresh должен отличаться от старого");
    }

    @Test
    void POST_refresh_reusedRefresh_returns401_stealDetected() throws Exception {
        // ADR-047 steal detection: после успешного rotation попытка
        // использовать старый refresh приводит к 401 + revoke chain
        registerUser("steal@example.com", "stealuser", "password1");
        String oldRefresh = loginAndGetRefreshCookie("steal@example.com", "password1");

        // Первый rotation - успех
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isOk());

        // Повторное использование старого = steal detected
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
    }

    @Test
    void POST_refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
    }

    @Test
    void POST_logout_clearsRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void POST_logout_withRefreshCookie_revokesInDb() throws Exception {
        // ADR-047: logout revoke'нет refresh - последующая попытка
        // refresh уже не пройдёт
        registerUser("logout@example.com", "logoutuser", "password1");
        String refreshToken = loginAndGetRefreshCookie("logout@example.com", "password1");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void POST_register_duplicateEmail_returns409WithoutRevealingEmail() throws Exception {
        // security: email enumeration hardening — detail не должен содержать
        // конкретный email адрес (информация для злоумышленника)
        var first = new RegisterRequest("secret@example.com", "userX", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        var second = new RegisterRequest("secret@example.com", "userY", "password2");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(containsString("email-already-taken")))
                // detail НЕ должен раскрывать email значение
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        containsString("secret@example.com"))));
    }

    @Test
    void POST_register_duplicateUsername_returns409WithoutRevealingUsername() throws Exception {
        // security: username enumeration hardening
        var first = new RegisterRequest("a@example.com", "uniqueuser", "password1");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        var second = new RegisterRequest("b@example.com", "uniqueuser", "password2");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(containsString("username-already-taken")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        containsString("uniqueuser"))));
    }

    // ---- helpers ----

    private void registerUser(String email, String username, String password) throws Exception {
        var req = new RegisterRequest(email, username, password);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        var req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String loginAndGetRefreshCookie(String email, String password) throws Exception {
        var req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie c = result.getResponse().getCookie("refresh_token");
        if (c == null) {
            throw new IllegalStateException("refresh_token cookie не пришла");
        }
        return c.getValue();
    }
}
