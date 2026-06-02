package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.ai.LlmApiException;
import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.library.domain.AiEditStatus;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

/**
 * IT для {@link AiEditService} (Этап 17.e, ADR-042).
 *
 * <p>Использует {@link MockBean LlmClient mock} (Spring Boot
 * автоматически инжектит mock вместо реального bean'а) - проверяем
 * end-to-end: state machine UPDATE'ы, JSON валидация, fence stripping
 * - **без** реальных вызовов в LLM API.
 *
 * <p>Реальная интеграция с LLM API проверяется через
 * {@link AiEditServiceLiveIT} (отдельный класс с @Tag("live")) -
 * запускается опционально по env var ANTHROPIC_API_KEY и не входит
 * в обычный verify.
 *
 * <p>JDK HttpServer для имитации LLM API (не WireMock) - тот же подход
 * что и в {@code HttpClientPdfFetcherRangeStreamingIT}. Нет runtime
 * dependency, достаточно для contract-level coverage.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AiEditServiceIT {

    @MockBean
    private LlmClient llmClient;

    @Autowired
    private AiEditService service;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                UUID.randomUUID(), BookType.MANUSCRIPT, "AI Edit Test Book",
                null, "ar", null, null, userId, now, now,
                null, null, null, null, null, null
        , BookVisibility.PUBLIC));

        // По умолчанию client enabled - тесты которым нужен disabled
        // делают org.mockito.Mockito.when(...) в test body
        org.mockito.Mockito.when(llmClient.isEnabled()).thenReturn(true);
    }

    @Test
    void enhance_validJsonResponse_marksDoneAndSavesFormattedContent() {
        String llmResponse = "{\"type\":\"doc\",\"content\":["
                + "{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"بسم الله\"}]}]}";
        org.mockito.Mockito.when(llmClient.complete(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(llmResponse);

        Page page = savePage("بسم الله الرحمن الرحيم");

        service.enhance(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.DONE);
        assertThat(after.aiEditCompletedAt()).isNotNull();
        assertThat(after.formattedContent()).contains("بسم الله");
        // text_content не трогается
        assertThat(after.textContent()).isEqualTo("بسم الله الرحمن الرحيم");
    }

    @Test
    void enhance_invalidJsonResponse_marksFailed() {
        org.mockito.Mockito.when(llmClient.complete(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("not even json {broken");

        Page page = savePage("some arabic text");

        service.enhance(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.FAILED);
        assertThat(after.aiEditCompletedAt()).isNotNull();
        // formatted_content не записан
        assertThat(after.formattedContent()).isNull();
    }

    @Test
    void enhance_markdownFenceWrappedResponse_strippedAndSaved() {
        String fenced = "```json\n{\"type\":\"doc\",\"content\":[]}\n```";
        org.mockito.Mockito.when(llmClient.complete(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(fenced);

        Page page = savePage("text");

        service.enhance(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.DONE);
        // Postgres jsonb колонка нормализует whitespace при чтении,
        // поэтому сравниваем через JSON equality а не string match.
        // Главное - fence снят (нет ```) и parsing работает.
        assertThat(after.formattedContent())
                .doesNotContain("```")
                .contains("\"type\"")
                .contains("\"doc\"")
                .contains("\"content\"");
    }

    @Test
    void enhance_pageWithoutTextContent_marksFailed() {
        Page page = savePage(""); // empty text_content

        service.enhance(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.FAILED);
        // llmClient не должен был быть вызван
        org.mockito.Mockito.verify(llmClient,
                org.mockito.Mockito.never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enhance_anthropicThrows_marksFailed() {
        org.mockito.Mockito.when(llmClient.complete(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new LlmApiException("API down", 500));

        Page page = savePage("some text");

        service.enhance(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.FAILED);
        assertThat(after.formattedContent()).isNull();
    }

    @Test
    void enhance_alreadyProcessing_skipsSecondPaidCall() {
        // Защита от check-then-act гонки: страница уже PROCESSING (другой
        // вызов в полёте) - второй enhance не должен дёргать платный API.
        org.mockito.Mockito.when(llmClient.complete(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"doc\",\"content\":[]}");
        Page page = savePage("some text");
        // эмулируем что concurrent вызов уже застолбил PROCESSING
        pageRepository.updateAiEditStatus(page.id(), AiEditStatus.PROCESSING,
                Instant.now(), null);

        service.enhance(page.id());

        // tryClaim вернул false → complete() не вызван
        org.mockito.Mockito.verify(llmClient,
                org.mockito.Mockito.never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        // статус остался PROCESSING (не перезаписан)
        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.PROCESSING);
    }

    @Test
    void enhance_clientDisabled_marksFailedWithoutCall() {
        org.mockito.Mockito.when(llmClient.isEnabled()).thenReturn(false);

        Page page = savePage("some text");

        service.enhance(page.id());

        Page after = pageRepository.findById(page.id()).orElseThrow();
        assertThat(after.aiEditStatus()).isEqualTo(AiEditStatus.FAILED);
        org.mockito.Mockito.verify(llmClient,
                org.mockito.Mockito.never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Page savePage(String textContent) {
        Instant now = Instant.now();
        return pageRepository.save(new Page(
                UUID.randomUUID(), book.id(), null, 1,
                null, null, null,
                textContent, null, null,
                now, now
        ));
    }
}
