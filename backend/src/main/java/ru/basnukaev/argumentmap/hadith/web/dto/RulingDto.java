package ru.basnukaev.argumentmap.hadith.web.dto;

/**
 * Вердикт учёного (alminasa rulings[]). Секция «Вердикты» Hadith Explorer.
 *
 * <p>{@code source} ('embedded'|'index') и {@code relatedExternalId} —
 * из {@code hd_rulings.metadata} (jsonb, ключи {@code source} /
 * {@code relatedExternalId}). UI обязан различать «вердикт на этот хадис»
 * (embedded) от «вердикта на параллельную передачу» (index +
 * relatedExternalId).
 */
public record RulingDto(
        String rulerName,
        Integer rulerDeathYear,
        String rulingText,
        String bookName,
        Integer page,
        Integer volume,
        String source,
        String relatedExternalId
) {
}
