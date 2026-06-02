package ru.basnukaev.argumentmap.library.imports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.ai.LlmApiException;
import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.exception.PageNotFoundException;
import ru.basnukaev.argumentmap.library.domain.AiEditStatus;
import ru.basnukaev.argumentmap.library.domain.Page;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

/**
 * AI editing pass (ADR-042, Этап 17.e). Преобразует OCR raw text из
 * {@link Page#textContent()} в structured ProseMirror JSON через LLM
 * ({@link LlmClient}, провайдер выбирается ai.provider - ADR-058) и
 * сохраняет в {@link Page#formattedContent()}.
 *
 * <p>Async через {@code @Async("aiEditTaskExecutor")} - REST endpoint
 * возвращает 202 Accepted сразу, тяжёлая LLM работа (5-15с) идёт в
 * bounded thread pool ({@link AiEditConfig}).
 *
 * <p>Цепочка:
 * <ol>
 *   <li>Load page + проверка наличия text_content (precondition)</li>
 *   <li>State PENDING/FAILED/DONE → PROCESSING + started_at=now</li>
 *   <li>Загрузить prompt template (cached), substitute %%OCR_TEXT%%</li>
 *   <li>{@link LlmClient#complete(String)} → raw JSON string</li>
 *   <li>Базовая валидация: распарсить JSON + проверить {@code type=doc}
 *       + {@code content[]} array</li>
 *   <li>Save через {@link PageRepository#updateFormattedContentAndMarkAiEditDone}
 *       (атомарный update formatted_content + ai_edit_status=DONE +
 *       completed_at=now)</li>
 *   <li>На exception: ai_edit_status=FAILED + log.error</li>
 * </ol>
 *
 * <p>{@code LlmClient.isEnabled()} - precondition. Если ключа нет,
 * {@link #enhanceAsync} сразу bypass + page FAILED. Controller
 * проверяет isEnabled() до триггера и возвращает 503 синхронно вместо
 * background failure.
 */
@Service
public class AiEditService {

    private static final Logger log = LoggerFactory.getLogger(AiEditService.class);

    /**
     * Placeholder для подстановки OCR текста в prompt template.
     * Простой строковый замен вместо Mustache/Freemarker -
     * one-shot substitution не оправдывает heavy template engine.
     */
    private static final String PROMPT_PLACEHOLDER = "%%OCR_TEXT%%";

    private static final String PROMPT_RESOURCE_PATH = "prompts/ai-edit-tahqiq.txt";

    private final PageRepository pageRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * Lazy-loaded prompt template (один раз на JVM). Volatile для
     * thread-safety при первом инициализующем чтении - после загрузки
     * immutable строка.
     */
    private volatile String promptTemplate;

    public AiEditService(PageRepository pageRepository,
                          LlmClient llmClient,
                          ObjectMapper objectMapper) {
        this.pageRepository = pageRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Async версия - возвращает сразу, тяжёлая работа в aiEditTaskExecutor.
     * REST endpoint возвращает 202 Accepted, polling endpoint показывает
     * актуальное состояние.
     */
    @Async("aiEditTaskExecutor")
    public void enhanceAsync(UUID pageId) {
        try {
            enhance(pageId);
        } catch (Exception e) {
            log.error("AI edit async задача упала для page {}: {}",
                    pageId, e.getMessage());
        }
    }

    /**
     * Synchronous helper - выполняет AI edit в caller thread. Используется
     * IT-тестами и internal callers которые управляют threading сами.
     *
     * <p>Все exception перехватываются → page помечается FAILED, не
     * пробрасывает дальше (caller-friendly).
     *
     * @throws PageNotFoundException 404 если pageId не существует
     *                               (PRE-condition, до перевода в PROCESSING)
     */
    public void enhance(UUID pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new PageNotFoundException(pageId));

        if (page.textContent() == null || page.textContent().isBlank()) {
            log.warn("AI edit пропущен для page {} - text_content пустой",
                    pageId);
            pageRepository.updateAiEditStatus(pageId, AiEditStatus.FAILED,
                    Instant.now(), Instant.now());
            return;
        }

        if (!llmClient.isEnabled()) {
            // Не должны попасть сюда если controller проверил isEnabled
            // заранее. Но safety net - помечаем FAILED.
            log.warn("AI edit пропущен для page {} - LlmClient disabled",
                    pageId);
            pageRepository.updateAiEditStatus(pageId, AiEditStatus.FAILED,
                    Instant.now(), Instant.now());
            return;
        }

        // Атомарный claim PROCESSING: если другой concurrent вызов уже в
        // PROCESSING - не делаем второй платный LLM-запрос. Защита от
        // double-submit / retry-в-полёте (check-then-act гонка).
        boolean claimed = pageRepository.tryClaimAiEditProcessing(
                pageId, AiEditStatus.PROCESSING, Instant.now());
        if (!claimed) {
            log.info("AI edit пропущен для page {} - уже PROCESSING "
                    + "(concurrent trigger), второй платный вызов не делаем", pageId);
            return;
        }

        try {
            String prompt = loadPromptTemplate().replace(PROMPT_PLACEHOLDER,
                    page.textContent());
            // Двухаргументный complete(null, prompt) — чтобы @Retry advice
            // на прокси LlmClient применился. Одноаргументная перегрузка
            // была default-методом интерфейса и self-invoke на raw target
            // обходила Spring-прокси (retry не срабатывал).
            String rawResponse = llmClient.complete(null, prompt);
            String validJson = validateProseMirrorJson(rawResponse);

            pageRepository.updateFormattedContentAndMarkAiEditDone(
                    pageId, validJson, Instant.now());
            log.info("AI edit success: page={} bytes={}",
                    pageId, validJson.length());

        } catch (RuntimeException e) {
            // LlmApiException extends RuntimeException, ловится здесь
            log.error("AI edit FAILED для page {}: {}",
                    pageId, e.getMessage(), e);
            pageRepository.updateAiEditStatus(pageId, AiEditStatus.FAILED,
                    null, Instant.now());
        }
    }

    /**
     * Базовая структурная валидация ProseMirror JSON. Проверяем:
     * <ul>
     *   <li>Валидный JSON (парсится)</li>
     *   <li>Корневой type = "doc"</li>
     *   <li>content - non-null array</li>
     * </ul>
     *
     * <p>Глубокая валидация (что content[i].type один из known nodes,
     * что attrs соответствуют schema) - отложена. Frontend Tiptap при
     * render игнорирует unknown node types без crash - acceptable
     * degradation на MVP.
     *
     * <p>Также пытается «починить» common LLM ошибки: markdown fence
     * вокруг JSON (```json ... ```), preface комментарии. Strip их.
     *
     * @return оригинальный JSON если валиден (либо очищенный от fence)
     * @throws LlmApiException если невалидный JSON либо неверная root
     *                         structure
     */
    String validateProseMirrorJson(String rawResponse) {
        String cleaned = stripMarkdownFence(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            String type = root.path("type").asText();
            if (!"doc".equals(type)) {
                throw new LlmApiException(
                        "LLM response не ProseMirror doc (type=" + type + ")",
                        200);
            }
            JsonNode content = root.get("content");
            if (content == null || !content.isArray()) {
                throw new LlmApiException(
                        "LLM response без content array", 200);
            }
            return cleaned;
        } catch (IOException e) {
            throw new LlmApiException(
                    "LLM вернул невалидный JSON: " + e.getMessage(), 200, e);
        }
    }

    /**
     * Удалить markdown code fence вокруг JSON если LLM проигнорировал
     * инструкцию «без fence». Patterns:
     * <pre>
     *   ```json\n{...}\n```
     *   ```\n{...}\n```
     *   ```{...}```
     * </pre>
     */
    private String stripMarkdownFence(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            // снимаем opening fence (возможно с language tag json)
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            // closing fence
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence > 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    /**
     * Lazy-load prompt template из classpath. Double-checked locking
     * через volatile - thread-safe на JDK 9+ (memory model gives us
     * publication ordering on volatile write).
     */
    private String loadPromptTemplate() {
        String local = promptTemplate;
        if (local == null) {
            synchronized (this) {
                local = promptTemplate;
                if (local == null) {
                    try (var stream = new ClassPathResource(PROMPT_RESOURCE_PATH)
                            .getInputStream()) {
                        local = new String(stream.readAllBytes(),
                                StandardCharsets.UTF_8);
                        promptTemplate = local;
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Не удалось загрузить prompt template " + PROMPT_RESOURCE_PATH,
                                e);
                    }
                }
            }
        }
        return local;
    }
}
