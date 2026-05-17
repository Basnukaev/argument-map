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
 * заполнены вместе, либо все NULL (page без скана).
 *
 * <p>{@code ocrStatus} - state machine для OCR pipeline (ADR-041):
 * {@code PENDING} (uploaded, ждёт OCR) → {@code PROCESSING} (в работе) →
 * {@code DONE} (text_content заполнен через Tesseract) или {@code FAILED}
 * (ошибка, см. лог). NULL = OCR не применим (PDF-imported, нет скана).
 * Timestamps {@code ocrStartedAt}/{@code ocrCompletedAt} для observability.
 *
 * <p>{@code aiEditStatus} - state machine для AI editing pass (ADR-042,
 * Этап 17.e). Та же 4-state машина что и OCR: {@code PENDING} →
 * {@code PROCESSING} → {@code DONE}/{@code FAILED}. NULL = AI edit
 * не запускался. При {@code DONE} результат лежит в {@code formattedContent}
 * (ProseMirror JSON). Timestamps {@code aiEditStartedAt}/{@code aiEditCompletedAt}
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
        String ocrStatus,
        Instant ocrStartedAt,
        Instant ocrCompletedAt,
        String aiEditStatus,
        Instant aiEditStartedAt,
        Instant aiEditCompletedAt,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Совместимостный конструктор - до миграции 34 (Этап 17.a) Page
     * имела 12 полей без image/OCR/AI. Существующие callers (shamela mapper,
     * IT-тесты) пользуются этим overload - 9 новых полей заполняются
     * null'ами автоматически.
     *
     * <p>Новый код для image-сканов / AI editing должен использовать
     * canonical 21-args constructor.
     */
    public Page(UUID id, UUID bookId, UUID chapterId, int pageNumber,
                String printedPage, String part, Integer pdfPageNumber,
                String textContent, String imageUrl, String formattedContent,
                Instant createdAt, Instant updatedAt) {
        this(id, bookId, chapterId, pageNumber, printedPage, part, pdfPageNumber,
                textContent, imageUrl, formattedContent,
                null, null, null, null, null, null,
                null, null, null,
                createdAt, updatedAt);
    }

    /**
     * Backward-compat 18-args конструктор - до миграции 35 (Этап 17.e)
     * Page имела OCR-поля но без AI-edit полей. Использовался
     * {@code OcrServiceIT}, {@code PageImageService} и др. Новые callers
     * AI edit pipeline должны использовать 21-args canonical конструктор.
     */
    public Page(UUID id, UUID bookId, UUID chapterId, int pageNumber,
                String printedPage, String part, Integer pdfPageNumber,
                String textContent, String imageUrl, String formattedContent,
                String imageBucket, String imageStorageKey, Instant imageUploadedAt,
                String ocrStatus, Instant ocrStartedAt, Instant ocrCompletedAt,
                Instant createdAt, Instant updatedAt) {
        this(id, bookId, chapterId, pageNumber, printedPage, part, pdfPageNumber,
                textContent, imageUrl, formattedContent,
                imageBucket, imageStorageKey, imageUploadedAt,
                ocrStatus, ocrStartedAt, ocrCompletedAt,
                null, null, null,
                createdAt, updatedAt);
    }
}
