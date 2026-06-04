package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Сам хадис (matn + meta). Vision 49d Section 2.6 Phase 1.c.
 *
 * <p>collectionId → hd_collections.id (FK): сборник хадисов - Бухари,
 * Муслим, и т.п. primaryNumber - номер в этом сборнике (e.g. 6018 для
 * "Действия по намерениям" из Бухари). Phase 5 (§11): выделенная
 * hd_collections вместо lib_books.
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
 * @param collectionId FK на сборник hd_collections (nullable если standalone)
 * @param primaryNumber номер в сборнике (e.g. 6018)
 * @param normalizedMatn нормализованный текст хадиса
 * @param status whitelist {@link HadithStatus}
 * @param sourceId nullable FK на sources для citation bridge
 * @param metadata JSONB extensible
 * @param createdAt timestamp
 */
public record Hadith(
        UUID id,
        UUID collectionId,
        Integer primaryNumber,
        String normalizedMatn,
        String status,
        UUID sourceId,
        String metadata,
        Instant createdAt,
        String externalSource,
        String externalId,
        String hadithType,
        String chapterAr,
        String subChapterAr,
        String fullTextAr
) {
    /**
     * Backward-compat конструктор без alminasa-полей (8 аргументов) для
     * существующих call-site'ов (seeder, IT-фикстуры; legacy sunnah-маппер
     * удалён Планом 4). alminasa-импортёр использует полный конструктор.
     */
    public Hadith(
            UUID id, UUID collectionId, Integer primaryNumber, String normalizedMatn,
            String status, UUID sourceId, String metadata, Instant createdAt
    ) {
        this(id, collectionId, primaryNumber, normalizedMatn, status, sourceId,
                metadata, createdAt, null, null, null, null, null, null);
    }
}
