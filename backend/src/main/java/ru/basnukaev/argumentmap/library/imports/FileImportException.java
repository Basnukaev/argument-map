package ru.basnukaev.argumentmap.library.imports;

/**
 * Бросается {@link FileImportService} при невозможности обработать
 * загруженный файл: повреждённый PDF, encrypted без пароля, нечитаемый
 * stream, отсутствие страниц. Маппится в Problem Details через
 * {@code GlobalExceptionHandler} (Этап 16.b) - 422 Unprocessable Entity.
 */
public class FileImportException extends RuntimeException {

    public FileImportException(String message) {
        super(message);
    }

    public FileImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
