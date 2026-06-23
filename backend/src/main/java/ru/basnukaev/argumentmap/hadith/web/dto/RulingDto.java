package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Вердикт учёного (alminasa rulings[]). Секция «Вердикты» Hadith Explorer.
 *
 * <p>{@code source} ('embedded'|'index') и {@code relatedExternalId} —
 * из {@code hd_rulings.metadata} (jsonb, ключи {@code source} /
 * {@code relatedExternalId}). UI обязан различать «вердикт на этот хадис»
 * (embedded) от «вердикта на параллельную передачу» (index +
 * relatedExternalId).
 *
 * <p>{@code relatedHadithId} (nullable) — резолв {@code relatedExternalId}
 * в наш FK, если параллельная передача уже импортирована (для перехода в
 * Explorer). {@code relatedCollectionNameRu} (nullable) — русское название
 * сборника параллельной передачи по префиксу {@code relatedExternalId}
 * ({@code bookId-…}); оба null когда {@code relatedExternalId} отсутствует
 * (embedded-вердикт на сам хадис) либо сборник/сиблинг неизвестен.
 *
 * <p>{@code id} — PK вердикта (нужен фронту для hide-тогла, ADR-065).
 * {@code hiddenByAdmin}/{@code hideReason} — reveal-режим курации (§4.3):
 * обычному читателю скрытый вердикт не приходит вовсе (вырезан), ADMIN видит
 * его с {@code hiddenByAdmin=true} + причиной, чтобы раскрыть обратно.
 */
public record RulingDto(
        UUID id,
        String rulerName,
        Integer rulerDeathYear,
        String rulingText,
        String bookName,
        Integer page,
        Integer volume,
        String source,
        String relatedExternalId,
        UUID relatedHadithId,
        String relatedCollectionNameRu,
        boolean hiddenByAdmin,
        String hideReason
) {
}
