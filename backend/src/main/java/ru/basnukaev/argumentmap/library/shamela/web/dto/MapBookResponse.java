package ru.basnukaev.argumentmap.library.shamela.web.dto;

import java.util.UUID;

/**
 * Ответ {@code POST /api/v1/admin/shamela/map-book/{bookId}}.
 *
 * <p>{@code bookId} - UUID созданной (или найденной существующей)
 * записи в {@code lib_books}. Можно использовать в
 * {@code GET /api/v1/library/books/{bookId}} для просмотра.
 *
 * <p>{@code created=false} - re-import: книга уже была замаплена в
 * прошлый раз, возвращена существующая. counts равны 0.
 */
public record MapBookResponse(
        UUID bookId,
        long shamelaBookId,
        boolean created,
        UUID authorityId,
        int chaptersCount,
        int pagesCount
) {
}
