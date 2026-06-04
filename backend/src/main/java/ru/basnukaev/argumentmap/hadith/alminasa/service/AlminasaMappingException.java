package ru.basnukaev.argumentmap.hadith.alminasa.service;

/**
 * Ошибка маппинга одного дока alminasa (план 3, решение 10). Бросается из
 * mapper-бинов; orchestration-цикл (план 5) ловит её, логирует, инкрементит
 * счётчик failed и продолжает — ошибка одного дока не валит весь прогон.
 */
public class AlminasaMappingException extends RuntimeException {

    private final String hadithId;
    private final String narratorId;

    public AlminasaMappingException(String message, String hadithId, String narratorId) {
        super(message);
        this.hadithId = hadithId;
        this.narratorId = narratorId;
    }

    public String hadithId() {
        return hadithId;
    }

    public String narratorId() {
        return narratorId;
    }
}
