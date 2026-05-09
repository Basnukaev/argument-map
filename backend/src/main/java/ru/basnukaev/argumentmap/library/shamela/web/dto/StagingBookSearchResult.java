package ru.basnukaev.argumentmap.library.shamela.web.dto;

/**
 * Запись в результатах поиска {@code GET /api/v1/admin/shamela/search}.
 * Обогащённый view над {@code lib_shamela_book} - содержит имя автора
 * через JOIN на staging-author и флаг "уже замаплена в lib_books"
 * через EXISTS-проверку. Используется фронтом admin-страницы для
 * отрисовки карточки результата с кнопкой "Импортировать"/"Открыть".
 */
public record StagingBookSearchResult(
        long bookId,
        String name,
        String authorName,
        int majorRelease,
        boolean isMapped
) {
}
