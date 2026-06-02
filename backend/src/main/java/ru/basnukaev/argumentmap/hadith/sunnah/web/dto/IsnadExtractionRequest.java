package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Запрос на извлечение иснада из матна хадиса (ADR-059). Принимаем
 * {@code collection} + {@code number}, а не сырой matn — сервер сам
 * достаёт матн из источника (preview-путь), не доверяя клиентскому
 * тексту.
 *
 * @param collection slug сборника (bukhari/muslim…)
 * @param number     числовой номер хадиса в сборнике
 */
public record IsnadExtractionRequest(
        @NotBlank String collection,
        @NotNull Integer number) {
}
