package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

public class ImageRegionNotFoundException extends RuntimeException {

    public ImageRegionNotFoundException(UUID id) {
        super("Image region с id=%s не найден".formatted(id));
    }
}
