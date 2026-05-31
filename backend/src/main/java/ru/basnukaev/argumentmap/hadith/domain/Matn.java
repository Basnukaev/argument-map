package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Текст вариации хадиса. Vision 49d Section 2.6 Phase 1.
 * Mirror schema hd_matns.
 *
 * <p>Один hadith может иметь несколько matns - разные publishing
 * variants. is_primary - основной matn.
 *
 * <p>collectionId → hd_collections.id (Phase 5 §11): сборник, из которого
 * взята эта вариация (была source_book_id → lib_books).
 */
public record Matn(
        UUID id,
        UUID hadithId,
        String textAr,
        String textArNormalized,
        String textRu,
        String textEn,
        UUID collectionId,
        Integer printedNumber,
        Integer pageNo,
        Integer volume,
        boolean isPrimary,
        String divergenceSummary,
        String metadata,
        Instant createdAt
) {
}
