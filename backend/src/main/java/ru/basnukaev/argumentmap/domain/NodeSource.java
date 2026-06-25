package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Привязка цитаты к узлу argument-map. Расширена positional полями по
 * ADR-027 для точной ссылки на место в источнике (text page+range, PDF
 * bbox, image region). Mode derived из заполненных полей - см. {@link #mode()}.
 *
 * После миграции 25 (FK variant A) - суррогатный `id UUID` PK позволяет
 * иметь N citations из одного source на одном узле (разные page/range/pdf).
 * `(node_id, source_id)` теперь FK constraints, не PK
 */
public record NodeSource(
        UUID id,
        UUID nodeId,
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
    public static NodeSource textMode(UUID nodeId, UUID sourceId,
                                      String quote, String context, String location,
                                      UUID pageId, int rangeStart, int rangeEnd,
                                      Instant createdAt) {
        return new NodeSource(UUID.randomUUID(), nodeId, sourceId, quote, context, location,
                pageId, rangeStart, rangeEnd,
                null, null, null,
                null,
                null,
                createdAt);
    }

    public static NodeSource pdfMode(UUID nodeId, UUID sourceId,
                                     String quote, String context, String location,
                                     UUID pdfFileId, int pdfPageNumber, String pdfBboxJson,
                                     Instant createdAt) {
        return new NodeSource(UUID.randomUUID(), nodeId, sourceId, quote, context, location,
                null, null, null,
                pdfFileId, pdfPageNumber, pdfBboxJson,
                null,
                null,
                createdAt);
    }

    /**
     * Citation на PDF-том FILE_ONLY книги (archive.org-скан) по 0-based
     * ordinal'у в pdf_links.files[] - ADR-067. Параллельно
     * {@link #pdfMode} (FK-вариант для user-upload).
     */
    public static NodeSource pdfLinkMode(UUID nodeId, UUID sourceId,
                                         String quote, String context, String location,
                                         int pdfFileIndex, int pdfPageNumber, String pdfBboxJson,
                                         Instant createdAt) {
        return new NodeSource(UUID.randomUUID(), nodeId, sourceId, quote, context, location,
                null, null, null,
                null, pdfPageNumber, pdfBboxJson,
                pdfFileIndex,
                null,
                createdAt);
    }

    public static NodeSource regionMode(UUID nodeId, UUID sourceId,
                                        String quote, String context, String location,
                                        UUID imageRegionId, Instant createdAt) {
        return new NodeSource(UUID.randomUUID(), nodeId, sourceId, quote, context, location,
                null, null, null,
                null, null, null,
                null,
                imageRegionId,
                createdAt);
    }

    public static NodeSource legacyMode(UUID nodeId, UUID sourceId,
                                        String quote, String context, String location,
                                        Instant createdAt) {
        return new NodeSource(UUID.randomUUID(), nodeId, sourceId, quote, context, location,
                null, null, null,
                null, null, null,
                null,
                null,
                createdAt);
    }

    public CitationMode mode() {
        return CitationMode.derive(pageId != null, pdfFileId != null,
                pdfFileIndex != null, imageRegionId != null);
    }
}
