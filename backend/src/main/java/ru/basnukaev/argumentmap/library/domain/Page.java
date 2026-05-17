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
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Совместимостный конструктор - до миграции 34 (Этап 17.a) Page
     * имела 12 полей без image/OCR. Существующие callers (shamela mapper,
     * IT-тесты) пользуются этим overload - 6 новых полей заполняются
     * null'ами автоматически.
     *
     * <p>Новый код для image-сканов должен использовать canonical
     * 18-args constructor либо builder-стиль через
     * {@code withImagePointer}/{@code withOcrStatus} helper'ы (см.
     * {@code PageImageService}/{@code OcrService}).
     */
    public Page(UUID id, UUID bookId, UUID chapterId, int pageNumber,
                String printedPage, String part, Integer pdfPageNumber,
                String textContent, String imageUrl, String formattedContent,
                Instant createdAt, Instant updatedAt) {
        this(id, bookId, chapterId, pageNumber, printedPage, part, pdfPageNumber,
                textContent, imageUrl, formattedContent,
                null, null, null, null, null, null,
                createdAt, updatedAt);
    }
}
