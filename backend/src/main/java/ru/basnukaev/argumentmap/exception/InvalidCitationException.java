package ru.basnukaev.argumentmap.exception;

public class InvalidCitationException extends RuntimeException {

    public InvalidCitationException(String reason) {
        super("Невалидная цитата: " + reason);
    }
}
