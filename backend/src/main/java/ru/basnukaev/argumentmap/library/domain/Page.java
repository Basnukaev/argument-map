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
 *
 * <p>{@code formattedContent} - ProseMirror JSON (jsonb колонка,
 * миграция 33, ADR-039). NULL = не редактировалось, frontend оборачивает
 * {@code textContent} в minimal paragraph-документ. Заполненное -
 * рендерится через {@code RichTextRenderer} с custom Tiptap extensions
 * (HadithBox / AyahBox / Marginalia / etc). Backend хранит как
 * сырую JSON-строку без структурной валидации - прозрачно проксирует.
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
        String formattedContent,
        Instant createdAt,
        Instant updatedAt
) {
}
