package ru.basnukaev.argumentmap.library.imports.web;

/**
 * Бросается {@link FileImportController} когда content type загружаемого
 * файла не входит в whitelist (Этап 16.b). Маппится в Problem Details
 * 415 Unsupported Media Type через {@code GlobalExceptionHandler}.
 */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
