package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

/**
 * Бросается когда narrator с указанным id не найден.
 * Маппится в 404 narrator-not-found через GlobalExceptionHandler.
 */
public class NarratorNotFoundException extends RuntimeException {

    private final UUID narratorId;

    public NarratorNotFoundException(UUID narratorId) {
        super("Narrator не найден: " + narratorId);
        this.narratorId = narratorId;
    }

    public UUID getNarratorId() {
        return narratorId;
    }
}
