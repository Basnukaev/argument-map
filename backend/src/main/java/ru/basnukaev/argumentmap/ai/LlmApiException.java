package ru.basnukaev.argumentmap.ai;

/**
 * Провайдер-агностичное исключение LLM-клиента (ADR-058, миграция из
 * AnthropicApiException). Бросается реализациями {@link LlmClient} при
 * failure запроса: non-2xx HTTP response, IOException, прерванный поток,
 * либо невалидный/нестандартный JSON ответ от LLM.
 *
 * <p>{@link #statusCode()} - HTTP код ответа провайдера (0 если запрос
 * вообще не успел дойти, e.g. IO error / timeout). Используется в
 * GlobalExceptionHandler для решения 503 vs 502 mapping и в
 * {@link LlmTransientFailurePredicate} для retry-решения.
 */
public class LlmApiException extends RuntimeException {

    private final int statusCode;

    public LlmApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public LlmApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
