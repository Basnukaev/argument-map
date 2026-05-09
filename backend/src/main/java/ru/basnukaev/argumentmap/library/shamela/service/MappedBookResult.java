package ru.basnukaev.argumentmap.library.shamela.service;

import java.util.UUID;

/**
 * Результат {@link ShamelaToLibraryMapper#mapBook}: какая `lib_books`-запись
 * создана/найдена и сколько глав/страниц замаплено.
 *
 * <p>{@code created=false} означает re-import (книга уже была замаплена в
 * прошлый раз - возвращаем существующую без создания дубликатов). В этом
 * случае counts равны 0 - повторное создание глав/страниц не делается.
 */
public record MappedBookResult(
        UUID bookId,
        long shamelaBookId,
        boolean created,
        UUID authorityId,
        int chaptersCount,
        int pagesCount
) {

    public static MappedBookResult freshlyCreated(UUID bookId, long shamelaBookId,
                                                  UUID authorityId,
                                                  int chaptersCount, int pagesCount) {
        return new MappedBookResult(bookId, shamelaBookId, true,
                authorityId, chaptersCount, pagesCount);
    }

    public static MappedBookResult alreadyMapped(UUID bookId, long shamelaBookId, UUID authorityId) {
        return new MappedBookResult(bookId, shamelaBookId, false, authorityId, 0, 0);
    }
}
