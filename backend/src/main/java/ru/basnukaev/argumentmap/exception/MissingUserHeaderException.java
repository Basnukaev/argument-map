package ru.basnukaev.argumentmap.exception;

public class MissingUserHeaderException extends RuntimeException {

    public MissingUserHeaderException(String reason) {
        super(reason);
    }
}
