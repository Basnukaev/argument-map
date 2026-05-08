package ru.basnukaev.argumentmap.exception;

public class InvalidBookException extends RuntimeException {

    public InvalidBookException(String reason) {
        super("Невалидная книга: " + reason);
    }
}
