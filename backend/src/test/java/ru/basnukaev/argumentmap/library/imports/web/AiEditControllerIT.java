package ru.basnukaev.argumentmap.library.imports.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.library.domain.AiEditStatus;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.imports.AnthropicClient;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

/**
 * Web layer IT для {@link AiEditController} через MockMvc (Этап 17.e,
 * ADR-042). Проверяет статусы / DTO body / Problem Details mapping
 * через {@code GlobalExceptionHandler}.
 *
 * <p>{@link AnthropicClient} замокан - тесты не делают реальных HTTP
 * вызовов в Anthropic. Логика самого client уже покрыта
 * {@link ru.basnukaev.argumentmap.library.imports.AnthropicClientStubIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiEditControllerIT {

    @MockBean
    private AnthropicClient anthropicClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PageRepository pageRepository;

    private UUID userId;
    private Book book;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                userId, "user-" + userId, userId + "@example.com");
        Instant now = Instant.now();
        book = bookRepository.save(new Book(
                UUID.randomUUID(), BookType.MANUSCRIPT, "Ctrl Test",
                null, "ar", null, null, userId, now, now,
                null, null, null, null, null, null
        ));
    }

    @Test
    void POST_aiEdit_enabledClient_returns202() throws Exception {
        Mockito.when(anthropicClient.isEnabled()).thenReturn(true);
        // complete() будет вызван в background async - не ждём результата
        Mockito.when(anthropicClient.complete(ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"doc\",\"content\":[]}");

        Page page = savePage("text");

        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/ai-edit")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pageId").value(page.id().toString()))
                .andExpect(jsonPath("$.status").value(AiEditStatus.PENDING))
                .andExpect(jsonPath("$.hasTextContent").value(true));
    }

    @Test
    void POST_aiEdit_disabledClient_returns503() throws Exception {
        Mockito.when(anthropicClient.isEnabled()).thenReturn(false);

        Page page = savePage("text");

        mockMvc.perform(post("/api/v1/library/pages/" + page.id() + "/ai-edit")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/ai-edit-not-configured"))
                .andExpect(jsonPath("$.title").value("AI editing не настроен"));
    }

    @Test
    void POST_aiEdit_unknownPage_returns404() throws Exception {
        Mockito.when(anthropicClient.isEnabled()).thenReturn(true);
        UUID bogus = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/library/pages/" + bogus + "/ai-edit")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://argumentmap.example/errors/page-not-found"));
    }

    @Test
    void GET_aiEditStatus_existingPage_returns200WithNullStatus() throws Exception {
        Mockito.when(anthropicClient.isEnabled()).thenReturn(true);
        Page page = savePage("text");

        mockMvc.perform(get("/api/v1/library/pages/" + page.id() + "/ai-edit")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId").value(page.id().toString()))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.hasTextContent").value(true));
    }

    @Test
    void GET_aiEditStatus_unknownPage_returns404() throws Exception {
        UUID bogus = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/library/pages/" + bogus + "/ai-edit")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    private Page savePage(String textContent) {
        Instant now = Instant.now();
        return pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                textContent, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                now, now
        ));
    }
}
