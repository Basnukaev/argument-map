package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Сам хадис (matn + meta). Vision 49d Section 2.6 Phase 1.c.
 *
 * <p>primaryBookId → lib_books.id (FK): каноническая публикация - Бухари,
 * Муслим, и т.п. primaryNumber - номер в этой книге (e.g. 6018 для
 * "Действия по намерениям" из Бухари).
 *
 * <p>normalizedMatn - normalized арабский текст для search (без
 * tashkīl + letter normalization). Заполняется в service-слое.
 *
 * <p>sourceId → sources.id nullable: мост в existing citation domain
 * (node_sources имеют sourceType=HADITH, ссылаются на sources.id).
 * Hadith-domain - первичная сущность, Source - bridge для citation
 * UI.
 *
 * @param id surrogate UUID PK
 * @param primaryBookId FK на основную книгу (nullable если standalone)
 * @param primaryNumber номер в primary_book (e.g. 6018)
 * @param normalizedMatn нормализованный текст хадиса
 * @param status whitelist {@link HadithStatus}
 * @param sourceId nullable FK на sources для citation bridge
 * @param metadata JSONB extensible
 * @param createdAt timestamp
 */
public record Hadith(
        UUID id,
        UUID primaryBookId,
        Integer primaryNumber,
        String normalizedMatn,
        String status,
        UUID sourceId,
        String metadata,
        Instant createdAt
) {
}
