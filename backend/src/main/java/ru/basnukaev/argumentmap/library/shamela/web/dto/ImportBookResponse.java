package ru.basnukaev.argumentmap.library.shamela.web.dto;

/**
 * Ответ {@code POST /api/v1/admin/shamela/import-book/{bookId}}.
 *
 * <p>Сообщает сколько страниц/заголовков было записано в staging
 * ({@code lib_shamela_page}/{@code lib_shamela_title}). Это ещё не
 * доменная модель - после import-book нужно вызвать map-book чтобы
 * увидеть книгу в {@code lib_books}.
 */
public record ImportBookResponse(
        long bookId,
        int majorRelease,
        int pagesCount,
        int titlesCount
) {
}
