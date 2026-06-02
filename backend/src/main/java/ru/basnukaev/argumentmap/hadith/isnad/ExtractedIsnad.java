package ru.basnukaev.argumentmap.hadith.isnad;

import java.util.List;

/**
 * Иснад (цепочка передатчиков), извлечённый LLM из матна арабского
 * хадиса (ADR-059). Эфемерный результат превью — в БД не пишется.
 *
 * <p>Порядок {@link #narrators}: сверху вниз по матну — {@code
 * narrators[0]} = прямой источник составителя сборника (верх иснада в
 * тексте), {@code narrators[last]} = сподвижник (сахаби), ближайший к
 * Пророку ﷺ. Сборщик/книга в цепь НЕ входит (он не звено иснада).
 *
 * @param isnadFound  удалось ли вообще выделить цепочку из матна
 * @param narrators   передатчики в порядке матна (top → companion);
 *                    пустой список если {@code isnadFound=false}
 * @param cleanedMatn текст хадиса без иснад-префикса (может быть null)
 */
public record ExtractedIsnad(
        boolean isnadFound,
        List<ExtractedNarrator> narrators,
        String cleanedMatn) {
}
