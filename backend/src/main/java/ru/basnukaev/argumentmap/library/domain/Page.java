package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Страница книги. Поля {@code printedPage}/{@code part}/{@code pdfPageNumber}
 * введены в миграции 19 для source-first нумерации (ADR-021): ссылка
 * должна вести на ту же страницу в оригинальном издании. Все три
 * nullable - старые книги до миграции 19 получают NULL.
 *
 * <p>{@code pageNumber} - internal counter, используется для URL-state
 * и navigation order. {@code printedPage} - то что показываем
 * пользователю. {@code pdfPageNumber} заполняется когда у книги
 * подключён PDF (будущая фича).
 */
public record Page(
        UUID id,
        UUID bookId,
        UUID chapterId,
        int pageNumber,
        String printedPage,
        String part,
        Integer pdfPageNumber,
        String textContent,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
