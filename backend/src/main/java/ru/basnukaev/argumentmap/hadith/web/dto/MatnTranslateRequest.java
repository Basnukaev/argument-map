package ru.basnukaev.argumentmap.hadith.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело запроса AI-перевода матна (План 7). {@code lang} — целевой язык,
 * union-валидация ru|en через {@code @Pattern} (как frontend union literal).
 * {@code @NotNull} обязателен: {@code @Pattern} по контракту Bean Validation
 * пропускает null — без него пустое тело молча уходило бы в en-ветку.
 */
public record MatnTranslateRequest(
        @NotNull(message = "lang обязателен")
        @Pattern(regexp = "ru|en", message = "lang должен быть 'ru' либо 'en'")
        String lang
) {
}
