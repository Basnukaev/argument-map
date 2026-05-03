package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(UUID id) {
        super("Источник с id=%s не найден".formatted(id));
    }
}
