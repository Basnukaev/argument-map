package ru.basnukaev.argumentmap.library.imports;

/**
 * Бросается {@link AnthropicClient} при failure запроса к Anthropic
 * Messages API: non-2xx HTTP response, IOException, prerwannaya
 * trans, либо невалидный JSON ответ от LLM (ADR-042, Этап 17.e).
 *
 * <p>{@link #statusCode()} - HTTP код ответа Anthropic (0 если
 * запрос вообще не успел дойти, e.g. IO error / timeout). Используется
 * в GlobalExceptionHandler для решения 503 vs 502 vs 422 mapping.
 */
public class AnthropicApiException extends RuntimeException {

    private final int statusCode;

    public AnthropicApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AnthropicApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
