package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/** Шарх/иляль/гариб (alminasa explanation/علل/غريب). kind ∈ {SHARH, ILAL, GHARIB}. */
public record HadithExplanation(
        UUID id,
        UUID hadithId,
        String kind,
        String bookName,
        String author,
        Integer authorDeathYear,
        Integer page,
        Integer volume,
        String text,
        String metadata,
        Instant createdAt
) {
}
