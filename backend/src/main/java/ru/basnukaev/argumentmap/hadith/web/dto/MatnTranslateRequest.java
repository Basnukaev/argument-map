package ru.basnukaev.argumentmap.hadith.web.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Тело запроса AI-перевода матна (План 7). {@code lang} — целевой язык,
 * union-валидация ru|en через {@code @Pattern} (как frontend union literal).
 */
public record MatnTranslateRequest(
        @Pattern(regexp = "ru|en", message = "lang должен быть 'ru' либо 'en'")
        String lang
) {
}
