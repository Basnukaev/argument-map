package ru.basnukaev.argumentmap.library.shamela.api;

/**
 * Ошибки взаимодействия с shamela API: не-2xx HTTP, проблемы сети,
 * битый JSON, прерванный поток. Не маппится на 4xx-домены проекта,
 * потому что shamela - технический интеграционный канал, а не
 * пользовательская ошибка. ETL-сервисы оборачивают/логируют по
 * ситуации.
 */
public class ShamelaApiException extends RuntimeException {

    public ShamelaApiException(String message) {
        super(message);
    }

    public ShamelaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
