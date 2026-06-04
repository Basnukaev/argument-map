package ru.basnukaev.argumentmap.hadith.web.dto;

/** Шарх/иляль/гариб (alminasa explanation). kind ∈ {SHARH, ILAL, GHARIB}. */
public record ExplanationDto(
        String kind,
        String bookName,
        String author,
        Integer page,
        Integer volume,
        String text
) {
}
