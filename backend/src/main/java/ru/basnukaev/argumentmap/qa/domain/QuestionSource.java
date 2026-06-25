package ru.basnukaev.argumentmap.qa.domain;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;

/**
 * Привязка цитаты к question (Этап 19.b). Аналог {@code NodeSource}
 * - валидация ADR-018 platform pivot: тот же positional citation
 * stack (ADR-027/029) повторно применяется на новой сущности через
 * параллельную иерархию.
 *
 * <p>Mode derived из заполненных positional полей, не хранится
 * отдельно. CitationMode переиспользован из core domain.
 */
public record QuestionSource(
        UUID id,
        UUID questionId,
        UUID sourceId,
        String quote,
        String context,
        String location,
        UUID pageId,
        Integer rangeStart,
        Integer rangeEnd,
        UUID pdfFileId,
        Integer pdfPageNumber,
        String pdfBbox,
        Integer pdfFileIndex,
        UUID imageRegionId,
        Instant createdAt
) {
    public static QuestionSource textMode(UUID questionId, UUID sourceId,
                                          String quote, String context, String location,
                                          UUID pageId, int rangeStart, int rangeEnd,
                                          Instant createdAt) {
        return new QuestionSource(UUID.randomUUID(), questionId, sourceId, quote, context, location,
                pageId, rangeStart, rangeEnd,
                null, null, null,
                null,
                null,
                createdAt);
    }

    public static QuestionSource pdfMode(UUID questionId, UUID sourceId,
                                         String quote, String context, String location,
                                         UUID pdfFileId, int pdfPageNumber, String pdfBboxJson,
                                         Instant createdAt) {
        return new QuestionSource(UUID.randomUUID(), questionId, sourceId, quote, context, location,
                null, null, null,
                pdfFileId, pdfPageNumber, pdfBboxJson,
                null,
                null,
                createdAt);
    }

    /**
     * Citation на PDF-том FILE_ONLY книги по 0-based ordinal'у в
     * pdf_links.files[] - ADR-067. Параллельно {@link #pdfMode}.
     */
    public static QuestionSource pdfLinkMode(UUID questionId, UUID sourceId,
                                             String quote, String context, String location,
                                             int pdfFileIndex, int pdfPageNumber, String pdfBboxJson,
                                             Instant createdAt) {
        return new QuestionSource(UUID.randomUUID(), questionId, sourceId, quote, context, location,
                null, null, null,
                null, pdfPageNumber, pdfBboxJson,
                pdfFileIndex,
                null,
                createdAt);
    }

    public static QuestionSource regionMode(UUID questionId, UUID sourceId,
                                            String quote, String context, String location,
                                            UUID imageRegionId, Instant createdAt) {
        return new QuestionSource(UUID.randomUUID(), questionId, sourceId, quote, context, location,
                null, null, null,
                null, null, null,
                null,
                imageRegionId,
                createdAt);
    }

    public CitationMode mode() {
        return CitationMode.derive(pageId != null, pdfFileId != null,
                pdfFileIndex != null, imageRegionId != null);
    }
}
