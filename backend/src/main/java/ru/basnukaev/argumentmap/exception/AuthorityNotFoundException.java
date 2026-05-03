package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class AuthorityNotFoundException extends RuntimeException {

    public AuthorityNotFoundException(UUID id) {
        super("Авторитет с id=%s не найден".formatted(id));
    }
}
