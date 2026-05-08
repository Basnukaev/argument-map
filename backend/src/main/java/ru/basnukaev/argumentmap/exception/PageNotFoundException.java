package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class PageNotFoundException extends RuntimeException {

    public PageNotFoundException(UUID id) {
        super("Страница с id=%s не найдена".formatted(id));
    }
}
