package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Шарх/иляль/гариб (alminasa explanation). kind ∈ {SHARH, ILAL, GHARIB}.
 * {@code reference} (nullable) — заголовок-СЛОВО гариб-статьи (из
 * {@code hd_explanations.metadata.reference}); null для SHARH/ILAL.
 *
 * <p>{@code id} — PK комментария (нужен фронту для hide-тогла, ADR-065).
 * {@code hiddenByAdmin}/{@code hideReason} — reveal-режим курации (§4.3):
 * читателю скрытый шарх не приходит, ADMIN видит с флагом + причиной.
 * {@code authorDeathYear} (nullable, г.х.) — год смерти автора толкования;
 * курируемое поле (whitelist {@code author_death_year}), симметрично
 * {@code NarratorCommentaryDto.commenterDeathYear}.
 */
public record ExplanationDto(
        UUID id,
        String kind,
        String bookName,
        String author,
        Integer authorDeathYear,
        Integer page,
        Integer volume,
        String text,
        String reference,
        boolean hiddenByAdmin,
        String hideReason
) {
}
