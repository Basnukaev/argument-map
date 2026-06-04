package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/**
 * Одно звено иснада, распарсенное из rawy-тега {@code full_text_ar}.
 *
 * <p>{@code externalId} — id рави из атрибута тега ({@code <a class=rawy id=N>});
 * {@code nameInText} — содержимое тега (имя в падежной форме, для резолва НЕ
 * источник — реш. 3 плана); {@code receivedVia} — нормализованное формула-слово
 * из сегмента ПОСЛЕ закрывающего тега (семантика «как этот рави получил хадис от
 * следующего звена», реш. 2 плана), может быть {@code null}, если формула не найдена.
 */
public record IsnadLink(
        String externalId,
        String nameInText,
        String receivedVia
) {
}
