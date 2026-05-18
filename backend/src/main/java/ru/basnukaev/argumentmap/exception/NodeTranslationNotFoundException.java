package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class NodeTranslationNotFoundException extends RuntimeException {

    public NodeTranslationNotFoundException(UUID id) {
        super("Перевод узла с id=%s не найден".formatted(id));
    }
}
