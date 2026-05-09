package ru.basnukaev.argumentmap.library.shamela.service;

/**
 * Ошибка уровня {@code ShamelaImportService}: невалидное состояние
 * staging (книга, которой нет в {@code lib_shamela_book}), сбой создания
 * рабочего каталога, нарушение инварианта pipeline (отсутствие
 * ожидаемого SQLite-файла после распаковки).
 *
 * <p>Не оборачивает {@code ShamelaApiException}/{@code ShamelaArchiveException}/
 * {@code ShamelaReaderException} - все они уже {@code RuntimeException}
 * и пробрасываются как есть. REST-слой 15.6 единым {@code @ControllerAdvice}
 * замапит каждый тип в свой HTTP-код.
 */
public class ShamelaImportException extends RuntimeException {

    public ShamelaImportException(String message) {
        super(message);
    }

    public ShamelaImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
