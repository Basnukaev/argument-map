package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Джарх/таʿдиль-цитата учёного-критика О передатчике (alminasa
 * narrator-commentary-12, ADR-061). Привязана к рави (FK на hd_narrators),
 * НЕ к хадису — поэтому отдельная таблица, не hd_explanations.
 *
 * <p>{@code commenter} — критик (краткое имя), {@code commenterDeathYear} —
 * год его смерти (хронологическая сортировка). {@code comments} — массив
 * строк-вердиктов (обычно 1, бывает >1; хранится как jsonb).
 * {@code bookName}/{@code author}/{@code page}/{@code volume} — атрибуция
 * источника (риджаль-книга).
 */
public record NarratorCommentary(
        UUID id,
        UUID narratorId,
        String commenter,
        Integer commenterDeathYear,
        String bookName,
        String author,
        Integer page,
        Integer volume,
        List<String> comments,
        String metadata,
        Instant createdAt
) {
}
