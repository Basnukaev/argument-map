package ru.basnukaev.argumentmap.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Реализация {@link LlmClient} для DeepSeek (ADR-058). DeepSeek
 * OpenAI-совместим - тот же wire-формат Chat Completions, поэтому
 * наследуется от {@link OpenAiCompatibleLlmClient}, передавая лишь свой
 * конфиг ({@code ai.deepseek.*}: base-url по умолчанию
 * https://api.deepseek.com, model deepseek-chat).
 *
 * <p>Активна при {@code ai.provider=deepseek}.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "deepseek")
public class DeepSeekLlmClient extends OpenAiCompatibleLlmClient {

    @Autowired
    public DeepSeekLlmClient(
            ObjectMapper objectMapper,
            @Value("${ai.deepseek.api-key:disabled}") String apiKey,
            @Value("${ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${ai.deepseek.model:deepseek-chat}") String model,
            @Value("${ai.deepseek.max-tokens:4096}") int maxTokens,
            @Value("${ai.deepseek.timeout-seconds:60}") int timeoutSeconds,
            @Value("${ai.http.proxy:}") String proxyUrl) {
        super(objectMapper, apiKey, baseUrl, model, maxTokens, timeoutSeconds, proxyUrl,
                "DeepSeekLlmClient");
    }
}
