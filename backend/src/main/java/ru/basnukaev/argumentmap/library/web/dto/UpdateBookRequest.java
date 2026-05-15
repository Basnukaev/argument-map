package ru.basnukaev.argumentmap.library.web.dto;

/**
 * Partial update academic metadata книги (Этап 20.d). Все поля
 * необязательные с PATCH-семантикой:
 *
 * <ul>
 *   <li>{@code String} поля: {@code null} = no change (keep existing FK),
 *       empty string {@code ""} = clear FK to null, non-empty trimmed =
 *       {@code findOrCreate(name)} в справочнике + replace FK</li>
 *   <li>{@code Integer} поля: {@code null} = no change. Чтобы очистить
 *       edition/year приходится пока удалять книгу или править руками в
 *       SQL - acceptable edge case</li>
 * </ul>
 *
 * Title, authorityId, language, description, metadata - не редактируются
 * через этот endpoint. Они меняются только при map-book из shamela или
 * через future-расширенный POST /books.
 */
public record UpdateBookRequest(
        String muhaqqiqName,
        String publisherName,
        String publicationPlaceName,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
) {
}
