package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class HadithGradeNotFoundException extends RuntimeException {

    public HadithGradeNotFoundException(UUID id) {
        super("Оценка хадиса с id=%s не найдена".formatted(id));
    }
}
