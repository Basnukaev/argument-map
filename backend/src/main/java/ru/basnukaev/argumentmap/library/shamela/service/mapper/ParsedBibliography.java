package ru.basnukaev.argumentmap.library.shamela.service.mapper;

/**
 * Результат парсинга shamela-bibliography строки (поле
 * {@code lib_shamela_book.bibliography}). Каждое поле nullable - shamela
 * хранит произвольный неструктурированный arabic-текст, парсер
 * консервативен и оставляет {@code null} если markers нет.
 *
 * <p>Значения уже trim'нуты. {@code muhaqqiq} / {@code publisher} /
 * {@code publicationPlace} это сырые имена для {@code findOrCreate(name)}
 * в соответствующих справочниках (ADR-028 normalized middle path).
 *
 * <p>Год публикации может быть в двух календарях параллельно
 * ({@code "١٤٢٢ هـ - ٢٠٠١ م"}) - парсер извлекает оба независимо.
 * Возможны сценарии «только хиджра», «только григориан», «оба».
 */
public record ParsedBibliography(
        String muhaqqiq,
        String publisher,
        String publicationPlace,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
) {
    public static ParsedBibliography empty() {
        return new ParsedBibliography(null, null, null, null, null, null);
    }

    /**
     * Все поля null - bibliography была blank или непарсируемой.
     * Помогает caller'у решить применять ли merge-логику.
     */
    public boolean isEmpty() {
        return muhaqqiq == null
                && publisher == null
                && publicationPlace == null
                && editionNumber == null
                && publishedYearHijri == null
                && publishedYearGregorian == null;
    }
}
