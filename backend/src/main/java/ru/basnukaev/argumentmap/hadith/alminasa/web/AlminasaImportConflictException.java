package ru.basnukaev.argumentmap.hadith.alminasa.web;

/**
 * Запуск импорта alminasa при уже идущем (state RUNNING). Маппится в **409**
 * {@code alminasa-import-already-running} в {@code GlobalExceptionHandler}
 * (план 5, решение 2). Один single-thread executor сериализует ВСЕ виды
 * импорта — narrators при работающем hadiths тоже даёт 409 (осознанно).
 */
public class AlminasaImportConflictException extends RuntimeException {

    public AlminasaImportConflictException() {
        super("Импорт alminasa уже выполняется");
    }
}
