package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class EdgeNotFoundException extends RuntimeException {

    public EdgeNotFoundException(UUID id) {
        super("Ребро с id=%s не найдено".formatted(id));
    }
}
