package ru.basnukaev.argumentmap.hadith.sunnah.service;

/**
 * Хадис с указанным номером отсутствует в источнике дампа sunnah.com для
 * данного сборника (фазовый импорт preview/single, ADR-052). Бросается
 * {@code SunnahImportService} при валидации до staging. Маппится в
 * {@code 404 Not Found} {@code sunnah-hadith-not-found}.
 */
public class SunnahHadithNotFoundException extends RuntimeException {

    public SunnahHadithNotFoundException(String collection, String number) {
        super("Хадис " + number + " не найден в источнике sunnah для сборника " + collection);
    }
}
