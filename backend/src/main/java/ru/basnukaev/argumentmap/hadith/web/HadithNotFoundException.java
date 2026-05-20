package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

/**
 * Бросается когда хадис с указанным id не найден.
 * Маппится в 404 hadith-not-found через GlobalExceptionHandler.
 */
public class HadithNotFoundException extends RuntimeException {

    private final UUID hadithId;

    public HadithNotFoundException(UUID hadithId) {
        super("Хадис не найден: " + hadithId);
        this.hadithId = hadithId;
    }

    public UUID getHadithId() {
        return hadithId;
    }
}
