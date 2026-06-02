package ru.basnukaev.argumentmap.ai;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Predicate для Resilience4j {@code @Retry(name="llmApi")} (ADR-058,
 * миграция из AnthropicTransientFailurePredicate) - определяет какие
 * ошибки LLM API считать TRANSIENT (имеет смысл повторить) против
 * PERMANENT (повтор бесполезен - только множит cost и latency).
 *
 * <p>Проблема которую закрывает: без предиката retry срабатывал бы на
 * ЛЮБОЙ {@link LlmApiException}, включая 4xx (400 bad request, 401
 * invalid key, 403 forbidden, 404). Это постоянные ошибки - 3 попытки с
 * exponential backoff (2s+4s+8s) только тратят деньги/время и оттягивают
 * FAILED-сигнал, не имея шанса на успех.
 *
 * <p>Transient (retry):
 * <ul>
 *   <li>{@link IOException} - connection reset / DNS / network jitter</li>
 *   <li>{@link LlmApiException} с statusCode == 0 - запрос не дошёл
 *       (IO error / timeout, обёрнут в LlmApiException)</li>
 *   <li>{@link LlmApiException} с statusCode == 429 - rate limit,
 *       обычно очищается за секунды</li>
 *   <li>{@link LlmApiException} с statusCode 5xx - server error на
 *       стороне провайдера, временный</li>
 * </ul>
 *
 * <p>Permanent (НЕ retry): любая {@link LlmApiException} с 4xx
 * statusCode (400/401/403/404/...) и любые другие исключения. Невалидный
 * JSON-ответ от LLM маппится в statusCode 200 - тоже permanent, retry
 * того же запроса не поможет.
 *
 * <p>Регистрируется в {@code application.yml} через
 * {@code resilience4j.retry.instances.llmApi.retry-exception-predicate}.
 */
public class LlmTransientFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof IOException) {
            return true;
        }
        if (throwable instanceof LlmApiException ex) {
            int status = ex.statusCode();
            // 0 = запрос не дошёл (IO/timeout, обёрнут в исключение)
            if (status == 0) {
                return true;
            }
            // 429 rate limit + 5xx server errors - transient
            return status == 429 || (status >= 500 && status < 600);
        }
        // всё остальное - permanent, не повторяем
        return false;
    }
}
