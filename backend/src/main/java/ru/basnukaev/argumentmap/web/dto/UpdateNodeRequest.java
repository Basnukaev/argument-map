package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/nodes/{id}. Все поля опциональные - можно обновить
 * только содержимое (с записью revision), только координаты на канвасе
 * (без revision), bilingual-поля либо комбинацию.
 *
 * <p>Bilingual semantics (миграция 44):
 * <ul>
 *   <li>{@code translation} - пустая строка означает «очистить перевод».
 *       null = «не менять перевод» (поле отсутствует в JSON)</li>
 *   <li>{@code translationLang} - 'ru' | 'en'. Пустая строка означает
 *       очистить. Бэк валидирует: если translation задан non-empty, lang
 *       обязателен</li>
 *   <li>{@code originalLang} - 'ar' | 'ru' | 'en'. Пустая строка очищает</li>
 * </ul>
 *
 * <p>Note про сериализацию: Jackson по умолчанию пропускает поля с
 * default value, поэтому Java не может отличить «не пришло» от «пришло null».
 * Используем флаги *Provided для семантики «поле в JSON присутствует».
 * Бэк (NodeController) использует флаги чтобы решить - обновлять поле
 * либо игнорировать.
 */
public record UpdateNodeRequest(
        @Size(min = 1, max = 10000) String content,
        Double posX,
        Double posY,
        @Size(max = 10000) String translation,
        @Pattern(regexp = "ru|en|", message = "translationLang должен быть 'ru', 'en' либо пустой строкой для очистки")
        String translationLang,
        @Pattern(regexp = "ar|ru|en|", message = "originalLang должен быть 'ar', 'ru', 'en' либо пустой строкой для очистки")
        String originalLang
) {
}
