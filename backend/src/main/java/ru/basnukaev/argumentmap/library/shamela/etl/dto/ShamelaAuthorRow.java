package ru.basnukaev.argumentmap.library.shamela.etl.dto;

/**
 * Строка из {@code author.sqlite} master-архива shamela. {@code biography}
 * многострочная (как пришло), {@code deathYear} распарсен через
 * {@link ru.basnukaev.argumentmap.library.shamela.etl.SqliteValueParser#parseYearOrNull(String)}
 * (магическое {@code "99999"} - "год неизвестен" - превращается в null).
 */
public record ShamelaAuthorRow(
        long id,
        String name,
        String biography,
        Integer deathYear,
        boolean deleted
) {
}
