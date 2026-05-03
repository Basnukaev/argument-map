package ru.basnukaev.argumentmap.exception;

public class InvalidEdgeException extends RuntimeException {

    public InvalidEdgeException(String reason) {
        super("Невалидное ребро: " + reason);
    }
}
