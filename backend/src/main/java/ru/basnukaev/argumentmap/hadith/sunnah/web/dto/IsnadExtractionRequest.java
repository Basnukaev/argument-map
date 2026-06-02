package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на извлечение иснада из матна хадиса (ADR-059). Принимаем
 * {@code collection} + {@code number}, а не сырой matn — сервер сам
 * достаёт матн из источника (preview-путь), не доверяя клиентскому
 * тексту.
 *
 * <p>{@code number} — строка (а не int): номера хадисов хранятся как
 * varchar и допускают суффиксы вроде "1a" (см. {@code
 * SunnahHadithBrowseItem}). Зеркалит string-идентичность import-эндпоинта.
 *
 * @param collection slug сборника (bukhari/muslim…)
 * @param number     номер хадиса в сборнике (string, допускает "1a")
 */
public record IsnadExtractionRequest(
        @NotBlank String collection,
        @NotBlank String number) {
}
