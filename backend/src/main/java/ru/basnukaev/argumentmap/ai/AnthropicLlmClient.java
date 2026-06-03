package ru.basnukaev.argumentmap.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * Реализация {@link LlmClient} поверх Anthropic Messages API (ADR-058,
 * миграция из AnthropicClient). Default-провайдер: активен при
 * {@code ai.provider=anthropic} либо если property отсутствует
 * (matchIfMissing=true).
 *
 * <p>Тонкий клиент (~120 LOC) поверх {@code java.net.http.HttpClient}.
 * Намеренно без Anthropic Java SDK - один endpoint, простой JSON body,
 * не оправдывает heavy dependency.
 *
 * <p>Защищён Resilience4j {@code @Retry(name="llmApi")} - max 3 попытки
 * ТОЛЬКО на transient errors (429 rate limit, 5xx server error,
 * IOException/timeout). Permanent 4xx (400/401/403/404) НЕ повторяются.
 * Решает {@link LlmTransientFailurePredicate}. Конфиг в
 * {@code application.yml} {@code resilience4j.retry.instances.llmApi}.
 *
 * <p>System prompt: Anthropic Messages API принимает top-level поле
 * {@code system} (отдельно от messages). Если systemPrompt не blank -
 * добавляем его в body.
 *
 * <p>Timeout 60s - LLM генерация занимает 5-15с типично, 60s даёт запас
 * на cold start / network jitter.
 *
 * @see LlmClient
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

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
    /** Превентивный Proxy-Authorization (Basic) если задан ai.http.proxy с кредами; иначе null. */
    private final String proxyAuthHeader;

    @Autowired
    public AnthropicLlmClient(
            ObjectMapper objectMapper,
            @Value("${ai.anthropic.api-key:disabled}") String apiKey,
            @Value("${ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${ai.anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${ai.anthropic.max-tokens:4096}") int maxTokens,
            @Value("${ai.anthropic.timeout-seconds:60}") int timeoutSeconds,
            @Value("${ai.http.proxy:}") String proxyUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        // Опциональный прокси (ai.http.proxy) навешивается только на этот
        // HttpClient, не глобально (иначе S3/MinIO-трафик тоже пошёл бы через прокси).
        this.httpClient = LlmHttpClients.build(10, proxyUrl);
        this.proxyAuthHeader = LlmHttpClients.proxyAuthHeader(proxyUrl);
        log.info("AnthropicLlmClient init: model={}, baseUrl={}, enabled={}, proxy={}",
                model, baseUrl, isEnabled(),
                proxyUrl != null && !proxyUrl.isBlank() ? "yes" : "no");
    }

    /**
     * Конструктор для IT-тестов - позволяет inject custom HttpClient
     * (например указывающий на JDK HttpServer stub на localhost) и
     * полностью overwrite конфиг.
     */
    AnthropicLlmClient(HttpClient httpClient, ObjectMapper objectMapper,
                       String apiKey, String baseUrl, String model,
                       int maxTokens, int timeoutSeconds) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.proxyAuthHeader = null;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && !DISABLED_SENTINEL.equalsIgnoreCase(apiKey);
    }

    /**
     * Отправить system + user промпты в Claude Messages API + извлечь
     * text из первого content block ответа.
     *
     * <p>Retry через Resilience4j (до 3 attempts, exponential backoff)
     * ТОЛЬКО на transient failures - 429/5xx → {@link LlmApiException},
     * IOException/timeout (statusCode=0). Permanent 4xx пробрасываются
     * сразу без повтора (см. {@link LlmTransientFailurePredicate}).
     *
     * @throws LlmApiException на 4xx/5xx ответ либо IO error после
     *                         исчерпания retry
     * @throws IllegalStateException если клиент disabled (caller должен
     *                               проверять isEnabled())
     */
    @Override
    @Retry(name = RETRY_NAME)
    public String complete(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "AnthropicLlmClient disabled - ANTHROPIC_API_KEY не настроен");
        }

        String body = buildRequestBody(systemPrompt, userPrompt);
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json");
        if (proxyAuthHeader != null) {
            rb.header("Proxy-Authorization", proxyAuthHeader);
        }
        HttpRequest request = rb.POST(HttpRequest.BodyPublishers.ofString(body)).build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 == 2) {
                return extractText(response.body());
            }
            // 4xx/5xx - бросаем для retry либо bubble up. Логируем тело
            // для диагностики (но без api-key естественно).
            String snippet = response.body() != null && response.body().length() > 500
                    ? response.body().substring(0, 500) + "..." : response.body();
            log.warn("Anthropic API вернул HTTP {} - {}", status, snippet);
            throw new LlmApiException(
                    "Anthropic API ответил HTTP " + status, status);
        } catch (IOException e) {
            log.warn("Anthropic API IO error: {}", e.getMessage());
            throw new LlmApiException(
                    "Anthropic API недоступен: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmApiException(
                    "Anthropic API прерван: " + e.getMessage(), 0, e);
        }
    }

    /**
     * Сформировать JSON body для Messages API:
     * {@code {model, max_tokens, [system,] messages: [{role:"user",...}]}}.
     * ObjectMapper used вместо ручной string concatenation - безопасно
     * эскейпит arabic / специальные символы в prompt. System поле
     * добавляется top-level (не в messages) только если non-blank.
     */
    private String buildRequestBody(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("max_tokens", maxTokens);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                payload.put("system", systemPrompt);
            }
            payload.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", userPrompt
            )));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "не удалось сериализовать Anthropic request body", e);
        }
    }

    /**
     * Извлечь text из ответа Anthropic. Структура:
     * {@code {content: [{type:"text", text:"..."}], ...}}. Берём первый
     * text block. Если content пустой / нет text block - LlmApiException.
     */
    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.get("content");
            if (content == null || !content.isArray() || content.isEmpty()) {
                throw new LlmApiException(
                        "Anthropic response без content array", 200);
            }
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    return block.path("text").asText();
                }
            }
            throw new LlmApiException(
                    "Anthropic response без text block в content", 200);
        } catch (IOException e) {
            throw new LlmApiException(
                    "не удалось распарсить Anthropic response: " + e.getMessage(),
                    200, e);
        }
    }
}
