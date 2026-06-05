package ru.basnukaev.argumentmap.hadith.web.dto;

/**
 * Шарх/иляль/гариб (alminasa explanation). kind ∈ {SHARH, ILAL, GHARIB}.
 * {@code reference} (nullable) — заголовок-СЛОВО гариб-статьи (из
 * {@code hd_explanations.metadata.reference}); null для SHARH/ILAL.
 */
public record ExplanationDto(
        String kind,
        String bookName,
        String author,
        Integer page,
        Integer volume,
        String text,
        String reference
) {
}
