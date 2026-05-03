package ru.basnukaev.argumentmap.exception;

public class InvalidSourceException extends RuntimeException {

    public InvalidSourceException(String reason) {
        super("Невалидный источник: " + reason);
    }
}
