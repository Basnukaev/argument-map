package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

/**
 * Бросается когда матн с заданным id не найден (План 7, AI-перевод).
 * Маппится в 404 {@code matn-not-found} через GlobalExceptionHandler.
 */
public class MatnNotFoundException extends RuntimeException {

    private final UUID matnId;

    public MatnNotFoundException(UUID matnId) {
        super("Матн не найден: " + matnId);
        this.matnId = matnId;
    }

    public UUID getMatnId() {
        return matnId;
    }
}
