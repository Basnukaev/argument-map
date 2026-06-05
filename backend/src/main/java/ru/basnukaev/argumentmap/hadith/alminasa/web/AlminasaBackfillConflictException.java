package ru.basnukaev.argumentmap.hadith.alminasa.web;

/**
 * Запуск backfill-краула alminasa (علل/غريب) при уже идущем (state RUNNING).
 * Маппится в **409** {@code alminasa-backfill-already-running} в
 * {@code GlobalExceptionHandler} (План 8, решение 1, паттерн launcher'а С58).
 */
public class AlminasaBackfillConflictException extends RuntimeException {

    public AlminasaBackfillConflictException() {
        super("Backfill alminasa уже выполняется");
    }
}
