package ru.basnukaev.argumentmap.library.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Запрос на создание {@code ImageRegion} - выделенного прямоугольника
 * на странице-скане (Этап 17.c, ADR-041). Координаты нормализованные
 * (0..1) - не пиксельные, чтобы независимо от dpi сканера / зума
 * рендера регион оставался на том же месте (см. ADR-019 (6)).
 *
 * <p>{@code extractedText} опционально - может быть заполнен сразу
 * клиентом если пользователь вручную ввёл текст для региона, либо
 * оставлен null для последующего AI-recognition pipeline (ADR-057).
 *
 * <p>Валидация бизнес-правила {@code x+width <= 1 AND y+height <= 1}
 * обеспечивается CHECK constraint в БД (см. миграцию 16). Здесь только
 * proper Bean Validation на каждое поле в отдельности.
 *
 * @param x левая координата (0..1)
 * @param y верхняя координата (0..1)
 * @param width ширина (0&lt;w&lt;=1)
 * @param height высота (0&lt;h&lt;=1)
 * @param extractedText текст из региона, может быть null
 */
public record CreateImageRegionRequest(
        @NotNull
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "1.0", inclusive = true)
        Double x,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "1.0", inclusive = true)
        Double y,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax(value = "1.0", inclusive = true)
        Double width,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax(value = "1.0", inclusive = true)
        Double height,

        String extractedText
) {
}
