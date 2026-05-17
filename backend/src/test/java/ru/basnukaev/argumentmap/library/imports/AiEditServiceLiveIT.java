package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Опциональный live test - реальный вызов в Anthropic API через
 * настоящий API key из env (Этап 17.e, ADR-042).
 *
 * <p>Активируется только если в env есть {@code ANTHROPIC_API_KEY} -
 * иначе скипается через {@code @EnabledIfEnvironmentVariable}. На CI
 * без ключа - skip автоматический.
 *
 * <p>{@code @Tag("live")} - исключается из обычного {@code mvn verify}
 * (см. surefire/failsafe excludeTags в pom.xml для других live тестов,
 * либо запускать отдельно через
 * {@code mvn -Dgroups=live test -Dtest=AiEditServiceLiveIT}).
 *
 * <p>Стоимость одного прогона - ~$0.01 (короткий prompt + короткий
 * response). Запускать только при изменении prompt template / model /
 * AnthropicClient логики - не на каждом verify.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY",
        matches = "^sk-ant-.+",
        disabledReason = "ANTHROPIC_API_KEY env var не настроен - live test пропущен")
class AiEditServiceLiveIT {

    @Test
    void complete_realApiKey_returnsValidProseMirrorJson() throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        AnthropicClient client = new AnthropicClient(
                httpClient, objectMapper,
                apiKey,
                "https://api.anthropic.com",
                "claude-sonnet-4-6",
                2048,
                60);

        String shortPrompt = """
                Ты возвращаешь только валидный JSON, без markdown fence.
                Преобразуй текст в ProseMirror doc: {"type":"doc","content":[...]}.
                Используй один paragraph node со вложенным text.

                Текст: %s""".formatted("بسم الله الرحمن الرحيم");

        String response = client.complete(shortPrompt);

        // Распарсить и проверить базовую структуру
        JsonNode root = objectMapper.readTree(response.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", ""));
        assertThat(root.path("type").asText()).isEqualTo("doc");
        assertThat(root.get("content").isArray()).isTrue();
        // Содержит arabic text где-то в дереве
        assertThat(root.toString().getBytes(StandardCharsets.UTF_8))
                .isNotEmpty();
    }
}
