package ru.basnukaev.argumentmap.hadith.web;

/**
 * Бросается когда LLM-перевод недоступен ({@code LlmClient.isEnabled()
 * == false}, sentinel-ключ активного провайдера). Маппится в 503
 * {@code llm-not-configured} через GlobalExceptionHandler (План 7,
 * решение 4). Отдельное имя от {@code AiEditNotConfiguredException} —
 * та library-scoped, эта hadith-scoped.
 *
 * <p>Configuration issue, не bug: admin должен установить API key
 * активного провайдера (см. AI_PROVIDER) и перезапустить backend.
 */
public class MatnTranslationNotConfiguredException extends RuntimeException {

    public MatnTranslationNotConfiguredException() {
        super("AI-перевод не настроен — установите API key активного "
                + "LLM-провайдера (см. AI_PROVIDER) и перезапустите backend");
    }
}
