package ru.basnukaev.argumentmap.hadith.alminasa.web;

/**
 * Старт краулинга alminasa при уже идущем (живой RUNNING-claim).
 * Маппится в 409 Conflict в {@code GlobalExceptionHandler} (регистрация
 * handler'а — вместе с admin-контроллером).
 */
public class AlminasaCrawlConflictException extends RuntimeException {

    public AlminasaCrawlConflictException() {
        super("Краулинг alminasa уже выполняется");
    }
}
