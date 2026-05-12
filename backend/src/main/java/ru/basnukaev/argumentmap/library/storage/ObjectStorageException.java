package ru.basnukaev.argumentmap.library.storage;

/**
 * Ошибка операции с object storage (ADR-024). Обёртывает IO/S3
 * исключения для unified обработки в верхних слоях.
 */
public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectStorageException(String message) {
        super(message);
    }
}
