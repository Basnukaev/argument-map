package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

/**
 * Сводка о странице в списке книги. {@code pageNumber} - internal
 * navigation counter (URL-state). {@code printedPage} и {@code part} -
 * source-first отображение пользователю (миграция 19, ADR-021).
 * Оба nullable для книг до миграции 19 или с неструктурированным
 * источником.
 */
public record PageSummaryResponse(
        UUID id,
        int pageNumber,
        String printedPage,
        String part,
        UUID chapterId,
        boolean hasText,
        boolean hasImage
) {
}
