package ru.basnukaev.argumentmap.ai;

/**
 * Провайдер-агностичный интерфейс LLM-клиента (ADR-058). Скрывает за
 * собой конкретный API (Anthropic Messages / OpenAI Chat Completions /
 * DeepSeek) так что прикладные сервисы ({@code AiEditService},
 * {@code BookMetadataExtractionService}) зависят от абстракции, а не от
 * вендора.
 *
 * <p>Конкретная реализация выбирается через
 * {@code @ConditionalOnProperty(name="ai.provider")} - ровно ОДИН bean
 * активен в любой момент (anthropic по умолчанию через matchIfMissing).
 * Переключение провайдера = смена env-переменной {@code AI_PROVIDER},
 * без изменения кода.
 *
 * <p>Disabled mode: если API key не настроен (sentinel "disabled"),
 * {@link #isEnabled()} возвращает false. Caller'ы проверяют это до
 * вызова {@link #complete} и делают graceful fallback (503 / regex
 * парсер / formatted_content=null).
 */
public interface LlmClient {

    /**
     * true если клиент сконфигурирован (API key установлен, не sentinel
     * "disabled"). Caller проверяет до вызова complete - чтобы 503 /
     * fallback срабатывал синхронно, а не в фоне через FAILED.
     */
    boolean isEnabled();

    /**
     * Отправить запрос в LLM с system + user промптами и вернуть text
     * ответа.
     *
     * <p>Только этот двухаргументный метод - нет одноаргументной
     * перегрузки. Раньше существовал {@code default complete(String)},
     * но он был ловушкой: default-метод интерфейса self-invoke на raw
     * target обходил Spring-прокси, из-за чего {@code @Retry}-advice на
     * реализации не срабатывал. Callers без system-промпта передают
     * {@code complete(null, userPrompt)}.
     *
     * @param systemPrompt системная инструкция (роль/правила); если
     *                     null или blank - не отправляется
     * @param userPrompt   текст user message
     * @return raw text ответа (валидация - на стороне caller)
     */
    String complete(String systemPrompt, String userPrompt);
}
