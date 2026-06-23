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
        Integer publishedYearGregorian,
        // Thesis (академическая рисала) поля - для shamela-книг которые
        // на самом деле магистерские/докторские диссертации (миграция 58).
        // thesisDegree = ماجستير/دكتوراه, supervisor = إشراف,
        // institution = جامعة/كلية. Все nullable - обычные книги их не имеют.
        String thesisDegree,
        String thesisSupervisor,
        String thesisInstitution,
        // thesisPreparer = автор диссертации из маркера إعداد ("подготовил").
        // У диссертаций автор часто отсутствует в structured author_id и
        // указан ТОЛЬКО здесь - без этого поля он молча терялся. Маппер
        // использует его как fallback для author-резолвинга когда
        // structured author отсутствует. Schema-колонки НЕТ - имя резолвится
        // в обычную Authority(type=AUTHOR) через тот же путь, что и любой
        // shamela-автор.
        String thesisPreparer
) {
    public static ParsedBibliography empty() {
        return new ParsedBibliography(null, null, null, null, null, null, null, null, null, null);
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
                && publishedYearGregorian == null
                && thesisDegree == null
                && thesisSupervisor == null
                && thesisInstitution == null
                && thesisPreparer == null;
    }
}
