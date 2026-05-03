package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(UUID id) {
        super("Тема с id=%s не найдена".formatted(id));
    }
}
