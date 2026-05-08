package ru.basnukaev.argumentmap.library.shamela.etl;

/**
 * Ошибка распаковки или чтения транзитного архива shamela
 * (битый zip, path-traversal, недостаточно прав на target-каталог).
 */
public class ShamelaArchiveException extends RuntimeException {

    public ShamelaArchiveException(String message) {
        super(message);
    }

    public ShamelaArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
