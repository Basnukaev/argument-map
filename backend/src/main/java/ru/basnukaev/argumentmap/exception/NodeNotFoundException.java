package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class NodeNotFoundException extends RuntimeException {

    public NodeNotFoundException(UUID id) {
        super("Узел с id=%s не найден".formatted(id));
    }
}
