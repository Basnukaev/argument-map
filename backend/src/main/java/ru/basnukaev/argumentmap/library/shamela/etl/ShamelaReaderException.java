package ru.basnukaev.argumentmap.library.shamela.etl;

/**
 * Ошибка чтения SQLite-файлов shamela
 * (битый файл, отсутствие ожидаемой таблицы/колонки, JDBC-ошибка).
 */
public class ShamelaReaderException extends RuntimeException {

    public ShamelaReaderException(String message) {
        super(message);
    }

    public ShamelaReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
