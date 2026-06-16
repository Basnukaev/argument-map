package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.List;

/**
 * Джарх/таʿдиль-цитата учёного-критика О передатчике (alminasa
 * narrator-commentary, ADR-061). {@code commenter} — критик,
 * {@code commenterDeathYear} — год его смерти (nullable), атрибуция
 * источника — {@code bookName}/{@code author}/{@code page}/{@code volume}.
 * {@code comments} — массив строк-вердиктов (обычно 1, бывает >1).
 */
public record NarratorCommentaryDto(
        String commenter,
        Integer commenterDeathYear,
        String bookName,
        String author,
        Integer page,
        Integer volume,
        List<String> comments
) {
}
