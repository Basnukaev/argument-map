package ru.basnukaev.argumentmap.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.retry.annotation.Retry;

/**
 * Реализация {@link LlmClient} поверх OpenAI-совместимого Chat
 * Completions API (ADR-058). Активна при {@code ai.provider=openai}.
 *
 * <p>Wire-формат OpenAI используется большим числом провайдеров
 * (OpenAI, DeepSeek, локальные vLLM/Ollama-совместимые). Поэтому
 * {@link DeepSeekLlmClient} - тонкий subclass с другим конфигом, тот же
 * HTTP-протокол.
 *
 * <p>Протокол: POST {@code {baseUrl}/v1/chat/completions}, заголовок
 * {@code Authorization: Bearer {apiKey}}, body
 * {@code {model, max_tokens, messages:[{role:"system",...}?,{role:"user",...}]}}.
 * Ответ: {@code choices[0].message.content}.
 *
 * <p>Защищён Resilience4j {@code @Retry(name="llmApi")} с теми же
 * transient-семантиками что и AnthropicLlmClient (см.
 * {@link LlmTransientFailurePredicate}).
 *
 * @see LlmClient
 * @see DeepSeekLlmClient
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    /**
     * Sentinel значение по умолчанию - signal что API key не настроен.
     */
    public static final String DISABLED_SENTINEL = "disabled";

    private static final String RETRY_NAME = "llmApi";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final Duration timeout;

    @Autowired
    public OpenAiCompatibleLlmClient(
            ObjectMapper objectMapper,
            @Value("${ai.openai.api-key:disabled}") String apiKey,
            @Value("${ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${ai.openai.model:gpt-4o}") String model,
            @Value("${ai.openai.max-tokens:4096}") int maxTokens,
            @Value("${ai.openai.timeout-seconds:60}") int timeoutSeconds) {
        this(objectMapper, apiKey, baseUrl, model, maxTokens, timeoutSeconds, "OpenAiCompatibleLlmClient");
    }

    /**
     * Protected конструктор для subclass'ов (DeepSeek) - принимает уже
     * разрешённый конфиг и собственное имя клиента для лога. Создаёт
     * production HttpClient (connect-timeout 10s).
     */
    protected OpenAiCompatibleLlmClient(ObjectMapper objectMapper, String apiKey,
                                        String baseUrl, String model, int maxTokens,
                                        int timeoutSeconds, String clientName) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("{} init: model={}, baseUrl={}, enabled={}",
                clientName, model, baseUrl, isEnabled());
    }

    /**
     * Конструктор для IT-тестов - inject custom HttpClient (например
     * указывающий на JDK HttpServer stub) и overwrite конфиг.
     */
    OpenAiCompatibleLlmClient(HttpClient httpClient, ObjectMapper objectMapper,
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

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && !DISABLED_SENTINEL.equalsIgnoreCase(apiKey);
    }

    @Override
    @Retry(name = RETRY_NAME)
    public String complete(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "OpenAI-compatible LLM client disabled - API key не настроен");
        }

        String body = buildRequestBody(systemPrompt, userPrompt);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 == 2) {
                return extractContent(response.body());
            }
            String snippet = response.body() != null && response.body().length() > 500
                    ? response.body().substring(0, 500) + "..." : response.body();
            log.warn("OpenAI-compatible API вернул HTTP {} - {}", status, snippet);
            throw new LlmApiException(
                    "OpenAI-compatible API ответил HTTP " + status, status);
        } catch (IOException e) {
            log.warn("OpenAI-compatible API IO error: {}", e.getMessage());
            throw new LlmApiException(
                    "OpenAI-compatible API недоступен: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmApiException(
                    "OpenAI-compatible API прерван: " + e.getMessage(), 0, e);
        }
    }

    /**
     * Сформировать JSON body для Chat Completions API:
     * {@code {model, max_tokens, messages:[...]}}. System message (если
     * non-blank) идёт первым элементом messages с role="system", затем
     * user message.
     */
    private String buildRequestBody(String systemPrompt, String userPrompt) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("max_tokens", maxTokens);
            payload.put("messages", messages);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "не удалось сериализовать OpenAI-compatible request body", e);
        }
    }

    /**
     * Извлечь content из ответа Chat Completions. Структура:
     * {@code {choices:[{message:{role,content}}], ...}}. Берём
     * {@code choices[0].message.content}. Если choices пустой / нет
     * content - LlmApiException.
     */
    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new LlmApiException(
                        "OpenAI-compatible response без choices array", 200);
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || content.isNull()) {
                throw new LlmApiException(
                        "OpenAI-compatible response без message.content", 200);
            }
            return content.asText();
        } catch (IOException e) {
            throw new LlmApiException(
                    "не удалось распарсить OpenAI-compatible response: " + e.getMessage(),
                    200, e);
        }
    }
}
