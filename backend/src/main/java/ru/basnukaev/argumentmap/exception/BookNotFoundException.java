package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(UUID id) {
        super("Книга с id=%s не найдена".formatted(id));
    }
}
