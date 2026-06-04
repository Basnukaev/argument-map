package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

import java.util.List;

/**
 * Результат разбора иснада из {@code full_text_ar}.
 *
 * <p>{@code links} — звенья в порядке как в тексте (collector→companion);
 * {@code collectorPhrase} — нормализованная формула из сегмента ПЕРЕД первым тегом
 * (речь составителя сборника, реш. 2 плана), может быть {@code null}.
 */
public record ParsedIsnad(
        List<IsnadLink> links,
        String collectorPhrase
) {

    /** Пустой результат: нет rawy-тегов / null/blank вход. */
    public static ParsedIsnad empty() {
        return new ParsedIsnad(List.of(), null);
    }
}
