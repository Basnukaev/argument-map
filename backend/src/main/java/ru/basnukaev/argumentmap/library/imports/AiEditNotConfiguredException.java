package ru.basnukaev.argumentmap.library.imports;

/**
 * Бросается AI edit controller'ом когда {@code LlmClient} disabled
 * (API key активного провайдера = sentinel "disabled", default).
 * Маппится в Problem Details через {@code GlobalExceptionHandler} → 503
 * Service Unavailable с понятным detail (Этап 17.e, ADR-042; ADR-058).
 *
 * <p>Не RuntimeException наследник логической ошибки - configuration
 * issue, не bug. Пользовательное действие - admin должен установить
 * env var (ANTHROPIC_API_KEY / OPENAI_API_KEY / DEEPSEEK_API_KEY в
 * зависимости от AI_PROVIDER) и перезапустить backend.
 */
public class AiEditNotConfiguredException extends RuntimeException {

    public AiEditNotConfiguredException() {
        super("AI editing не настроен - установите API key активного "
                + "LLM-провайдера (см. AI_PROVIDER) и перезапустите backend");
    }
}
