package ru.basnukaev.argumentmap.library.imports;

/**
 * Бросается AI edit controller'ом когда AnthropicClient disabled
 * ({@code ANTHROPIC_API_KEY=disabled}, default). Маппится в Problem
 * Details через {@code GlobalExceptionHandler} → 503 Service Unavailable
 * с понятным detail (Этап 17.e, ADR-042).
 *
 * <p>Не RuntimeException наследник логической ошибки - configuration
 * issue, не bug. Пользовательное действие - admin должен установить
 * env var и перезапустить backend.
 */
public class AiEditNotConfiguredException extends RuntimeException {

    public AiEditNotConfiguredException() {
        super("AI editing не настроен - установите ANTHROPIC_API_KEY env var");
    }
}
