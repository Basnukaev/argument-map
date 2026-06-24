package ru.basnukaev.argumentmap.hadith.web;

/**
 * Бросается когда пользователь превысил лимит LLM-вызывающих translate-запросов
 * в sliding-window (cost-guard P2-3). Маппится в 429 {@code too-many-requests}
 * через GlobalExceptionHandler с заголовком {@code Retry-After} и
 * property {@code retryAfterSeconds} (тот же type-slug и shape, что у
 * inline-ответа {@code RateLimitFilter} для auth endpoints).
 *
 * <p>Считаются только запросы, реально идущие в LLM — повторный перевод из
 * кэша ({@code cached=true}), 403/404/422/503 бюджет не тратят (лимит
 * расходуется в {@code HadithTranslationService.translate} только перед
 * самим вызовом модели). ADMIN от лимита освобождён.
 */
public class MatnTranslateRateLimitExceededException extends RuntimeException {

    private final int limit;
    private final long retryAfterSeconds;

    public MatnTranslateRateLimitExceededException(int limit, long retryAfterSeconds) {
        super("Превышен лимит " + limit + " запросов AI-перевода. Повторите через "
                + retryAfterSeconds + " сек.");
        this.limit = limit;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getLimit() {
        return limit;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
