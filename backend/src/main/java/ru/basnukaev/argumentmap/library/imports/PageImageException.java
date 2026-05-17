package ru.basnukaev.argumentmap.library.imports;

/**
 * Бросается {@link PageImageService} при невозможности обработать
 * загруженное изображение: чтение stream'а, MIME validation, ошибки
 * S3 put (через wrapping ObjectStorageException). Маппится в Problem
 * Details через {@code GlobalExceptionHandler} - 422 Unprocessable
 * Entity (Этап 17.a, ADR-041).
 */
public class PageImageException extends RuntimeException {

    public PageImageException(String message) {
        super(message);
    }

    public PageImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
