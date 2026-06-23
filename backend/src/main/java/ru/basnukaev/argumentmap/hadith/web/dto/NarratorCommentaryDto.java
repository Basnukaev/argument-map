package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Джарх/таʿдиль-цитата учёного-критика О передатчике (alminasa
 * narrator-commentary, ADR-061). {@code commenter} — критик,
 * {@code commenterDeathYear} — год его смерти (nullable), атрибуция
 * источника — {@code bookName}/{@code author}/{@code page}/{@code volume}.
 * {@code comments} — массив строк-вердиктов (обычно 1, бывает >1).
 *
 * <p>{@code id} — PK цитаты (нужен фронту для hide-тогла, ADR-065).
 * {@code hiddenByAdmin}/{@code hideReason} — reveal-режим курации (§4.3):
 * читателю скрытая цитата заблудшего критика не приходит, ADMIN видит с
 * флагом + причиной. Verbatim {@code comments} НЕ правятся (первоисточник
 * риджаль-книги) — только скрытие записи целиком.
 */
public record NarratorCommentaryDto(
        UUID id,
        String commenter,
        Integer commenterDeathYear,
        String bookName,
        String author,
        Integer page,
        Integer volume,
        List<String> comments,
        boolean hiddenByAdmin,
        String hideReason
) {
}
