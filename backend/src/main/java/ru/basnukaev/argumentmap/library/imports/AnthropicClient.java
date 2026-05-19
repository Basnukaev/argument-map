package ru.basnukaev.argumentmap.library.imports;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.retry.annotation.Retry;

/**
 * HTTP-обёртка над Anthropic Messages API (ADR-042, Этап 17.e).
 *
 * <p>Тонкий клиент (~100 LOC) поверх {@code java.net.http.HttpClient}.
 * Намеренно без Anthropic Java SDK - один endpoint, простой JSON
 * body, не оправдывает heavy dependency. Если в будущем понадобятся
 * streaming responses / batch API - переехать на SDK.
 *
 * <p>Защищён Resilience4j {@code @Retry(name="anthropicApi")} - max
 * 3 попытки на transient errors (429 rate limit, 5xx server error,
 * IOException). Конфиг в {@code application.yml} {@code
 * resilience4j.retry.instances.anthropicApi}.
 *
 * <p>Disabled mode - если {@code ai.anthropic.api-key=disabled}
 * (default value), {@link #isEnabled()} возвращает false. AiEditService
 * проверяет до триггера задачи и возвращает 503 пользователю с
 * понятным сообщением «AI editing не настроен».
 *
 * <p>Timeout 60s - LLM генерация для arabic page занимает 5-15с
 * типично, 60s даёт запас на cold start / network jitter. Превышение
 * → IOException → retry либо bubble up в caller.
 *
 * @see AiEditService
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    /**
     * Sentinel значение по умолчанию - signal что API key не настроен.
     * Сравниваем case-sensitive (только этот exact literal).
     */
    public static final String DISABLED_SENTINEL = "disabled";

    private static final String RETRY_NAME = "anthropicApi";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final Duration timeout;

    public AnthropicClient(
            ObjectMapper objectMapper,
            @Value("${ai.anthropic.api-key:disabled}") String apiKey,
            @Value("${ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${ai.anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${ai.anthropic.max-tokens:4096}") int maxTokens,
            @Value("${ai.anthropic.timeout-seconds:60}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("AnthropicClient init: model={}, baseUrl={}, enabled={}",
                model, baseUrl, isEnabled());
    }

    /**
     * Конструктор для IT-тестов - позволяет inject custom HttpClient
     * (например указывающий на JDK HttpServer stub на localhost) и
     * полностью overwrite конфиг.
     */
    AnthropicClient(HttpClient httpClient, ObjectMapper objectMapper,
                    String apiKey, String baseUrl, String model,
                    int maxTokens, int timeoutSeconds) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * true если API key настроен (не дефолтный sentinel). AiEditService
     * вызывает до триггера async task - чтобы 503 вернулся синхронно,
     * а не в background через FAILED status.
     */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && !DISABLED_SENTINEL.equalsIgnoreCase(apiKey);
    }

    /**
     * Отправить user prompt в Claude Messages API + извлечь text из
     * первого content block ответа.
     *
     * <p>Retry через Resilience4j (3 attempts, exponential backoff)
     * на transient failures - 429/5xx → {@link AnthropicApiException},
     * IOException, InterruptedException.
     *
     * @param userPrompt полный текст user message (включая prompt
     *                   template + raw arabic text)
     * @return raw text response (ожидается valid JSON, но валидация
     *         делается caller'ом - AiEditService.enhance)
     * @throws AnthropicApiException на 4xx/5xx ответ либо IO error
     *                               после исчерпания retry
     * @throws IllegalStateException если клиент disabled (caller
     *                               должен проверять isEnabled())
     */
    @Retry(name = RETRY_NAME)
    public String complete(String userPrompt) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "AnthropicClient disabled - ANTHROPIC_API_KEY не настроен");
        }

        String body = buildRequestBody(userPrompt);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 == 2) {
                return extractText(response.body());
            }
            // 4xx/5xx - бросаем для retry либо bubble up.
            // Логируем тело для диагностики (но без api-key естественно).
            String snippet = response.body() != null && response.body().length() > 500
                    ? response.body().substring(0, 500) + "..." : response.body();
            log.warn("Anthropic API вернул HTTP {} - {}", status, snippet);
            throw new AnthropicApiException(
                    "Anthropic API ответил HTTP " + status, status);
        } catch (IOException e) {
            log.warn("Anthropic API IO error: {}", e.getMessage());
            throw new AnthropicApiException(
                    "Anthropic API недоступен: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnthropicApiException(
                    "Anthropic API прерван: " + e.getMessage(), 0, e);
        }
    }

    /**
     * Сформировать JSON body для Messages API:
     * {@code {model, max_tokens, messages: [{role: "user", content: ...}]}}.
     * ObjectMapper used вместо ручной string concatenation -
     * безопасно эскейпит arabic / специальные символы в prompt.
     */
    private String buildRequestBody(String userPrompt) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", userPrompt
                    ))
            );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "не удалось сериализовать Anthropic request body", e);
        }
    }

    /**
     * Извлечь text из ответа Anthropic. Структура:
     * {@code {content: [{type: "text", text: "..."}], ...}}.
     * Берём первый text block. Если content пустой / нет text block -
     * AnthropicApiException (LLM вернул что-то нестандартное).
     */
    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.get("content");
            if (content == null || !content.isArray() || content.isEmpty()) {
                throw new AnthropicApiException(
                        "Anthropic response без content array", 200);
            }
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    return block.path("text").asText();
                }
            }
            throw new AnthropicApiException(
                    "Anthropic response без text block в content", 200);
        } catch (IOException e) {
            throw new AnthropicApiException(
                    "не удалось распарсить Anthropic response: " + e.getMessage(),
                    200, e);
        }
    }
}
