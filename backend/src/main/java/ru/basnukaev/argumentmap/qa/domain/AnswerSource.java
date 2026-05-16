package ru.basnukaev.argumentmap.qa.domain;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.CitationMode;

/**
 * Привязка цитаты к answer (Этап 19.d). Аналог {@code QuestionSource} -
 * 3-я итерация ADR-033 параллельной иерархии. Подтверждает что platform
 * pivot (ADR-018) масштабируется без перехода на generic citations table.
 *
 * <p>Mode derived из заполненных positional полей, не хранится отдельно.
 * CitationMode переиспользован из core domain.
 */
public record AnswerSource(
        UUID id,
        UUID answerId,
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
        UUID imageRegionId,
        Instant createdAt
) {
    public static AnswerSource textMode(UUID answerId, UUID sourceId,
                                        String quote, String context, String location,
                                        UUID pageId, int rangeStart, int rangeEnd,
                                        Instant createdAt) {
        return new AnswerSource(UUID.randomUUID(), answerId, sourceId, quote, context, location,
                pageId, rangeStart, rangeEnd,
                null, null, null,
                null,
                createdAt);
    }

    public static AnswerSource pdfMode(UUID answerId, UUID sourceId,
                                       String quote, String context, String location,
                                       UUID pdfFileId, int pdfPageNumber, String pdfBboxJson,
                                       Instant createdAt) {
        return new AnswerSource(UUID.randomUUID(), answerId, sourceId, quote, context, location,
                null, null, null,
                pdfFileId, pdfPageNumber, pdfBboxJson,
                null,
                createdAt);
    }

    public static AnswerSource regionMode(UUID answerId, UUID sourceId,
                                          String quote, String context, String location,
                                          UUID imageRegionId, Instant createdAt) {
        return new AnswerSource(UUID.randomUUID(), answerId, sourceId, quote, context, location,
                null, null, null,
                null, null, null,
                imageRegionId,
                createdAt);
    }

    public CitationMode mode() {
        return CitationMode.derive(pageId != null, pdfFileId != null, imageRegionId != null);
    }
}
