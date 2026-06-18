package ru.basnukaev.argumentmap.hadith.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Тело запроса ручной правки сохранённого AI-перевода матна (ADMIN-only).
 * Админ перезаписывает существующий {@code text_ru}/{@code text_en} без
 * вызова LLM — это правка, а не генерация. {@code lang} — целевая колонка,
 * union-валидация ru|en через {@code @Pattern} (зеркалит {@link MatnTranslateRequest}).
 * {@code @NotNull} обязателен: {@code @Pattern} по контракту Bean Validation
 * пропускает null. {@code text} — новый перевод, не пустой ({@code @NotBlank}).
 * Колонка {@code text_ru}/{@code text_en} — TEXT (без лимита в БД), но
 * {@code @Size} ставит разумный потолок против вставки гигантских строк.
 */
public record MatnTranslationEditRequest(
        @NotNull(message = "lang обязателен")
        @Pattern(regexp = "ru|en", message = "lang должен быть 'ru' либо 'en'")
        String lang,

        @NotBlank(message = "text обязателен и не может быть пустым")
        @Size(max = 50_000, message = "text не должен превышать 50000 символов")
        String text
) {
}
