package ru.basnukaev.argumentmap.hadith.alminasa.api;

import java.util.function.Predicate;

/**
 * Resilience4j-предикат retry-инстанса {@code alminasaApi} (как
 * {@code LlmTransientFailurePredicate} у llmApi): ретраим только transient —
 * 5xx, 429, I/O (statusCode 0). 4xx и interrupt (-1) — не ретраим.
 */
public class AlminasaTransientFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof AlminasaApiException e) {
            return e.statusCode() == 0 || e.statusCode() == 429 || e.statusCode() >= 500;
        }
        return false;
    }
}
