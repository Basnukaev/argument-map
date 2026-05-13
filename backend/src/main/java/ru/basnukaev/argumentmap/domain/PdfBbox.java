package ru.basnukaev.argumentmap.domain;

/**
 * Прямоугольная область на PDF странице. Координаты нормализованы 0-1
 * относительно page viewport - zoom-invariant, работает при любом
 * PDF.js scale / DPI / экран. См. ADR-027.
 *
 * <p>Валидация:
 * <ul>
 *   <li>{@code x}, {@code y} ∈ [0, 1]</li>
 *   <li>{@code width}, {@code height} ∈ (0, 1]</li>
 *   <li>{@code x + width} ≤ 1, {@code y + height} ≤ 1 (rectangle полностью внутри page)</li>
 * </ul>
 */
public record PdfBbox(double x, double y, double width, double height) {

    private static final double EPSILON = 0.001;

    public PdfBbox {
        if (x < 0.0 || x > 1.0
                || y < 0.0 || y > 1.0
                || width <= 0.0 || width > 1.0
                || height <= 0.0 || height > 1.0
                || x + width > 1.0 + EPSILON
                || y + height > 1.0 + EPSILON) {
            throw new IllegalArgumentException(
                "PdfBbox coords должны быть в [0,1] и x+w/y+h <= 1, получено: "
                    + "x=" + x + " y=" + y + " w=" + width + " h=" + height);
        }
    }
}
