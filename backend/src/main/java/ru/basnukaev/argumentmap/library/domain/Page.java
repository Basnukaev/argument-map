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
 *
 * <p>{@code imageBucket} / {@code imageStorageKey} / {@code imageUploadedAt} -
 * pointer на uploaded скан страницы в MinIO bucket
 * {@code library-page-images} (миграция 34, ADR-041). Все три либо
 * заполнены вместе, либо все NULL (page без скана). Субстрат для
 * будущего AI-recognition (ADR-057).
 *
 * <p>{@code aiEditStatus} - state machine для AI editing pass (ADR-042,
 * Этап 17.e): {@code PENDING} → {@code PROCESSING} → {@code DONE}/
 * {@code FAILED}. NULL = AI edit не запускался. При {@code DONE}
 * результат лежит в {@code formattedContent} (ProseMirror JSON).
 * Timestamps {@code aiEditStartedAt}/{@code aiEditCompletedAt}
 * для observability.
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
        String imageBucket,
        String imageStorageKey,
        Instant imageUploadedAt,
        String aiEditStatus,
        Instant aiEditStartedAt,
        Instant aiEditCompletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Совместимостный конструктор - до миграции 34 (Этап 17.a) Page
     * имела 12 полей без image/AI. Существующие callers (shamela mapper,
     * IT-тесты) пользуются этим overload - 6 новых полей заполняются
     * null'ами автоматически.
     *
     * <p>Новый код для image-сканов / AI editing должен использовать
     * canonical 18-args constructor.
     */
    public Page(UUID id, UUID bookId, UUID chapterId, int pageNumber,
                String printedPage, String part, Integer pdfPageNumber,
                String textContent, String imageUrl, String formattedContent,
                Instant createdAt, Instant updatedAt) {
        this(id, bookId, chapterId, pageNumber, printedPage, part, pdfPageNumber,
                textContent, imageUrl, formattedContent,
                null, null, null,
                null, null, null,
                createdAt, updatedAt);
    }
}
