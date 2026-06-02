package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Сборник хадисов (Сахих аль-Бухари, Сахих Муслим, Муватта и т.п.).
 * Phase 5 ETL (этап 49.C, §11): выделенная сущность вместо reuse lib_books —
 * сборник хадисов концептуально отличается от книги-публикации library-домена
 * (нет pages/OCR/visibility/members), расширяема под hadith-специфику.
 *
 * <p>compilerNarratorId → hd_narrators.id: автор-составитель как передатчик
 * (Бухари-человек уже присутствует в hd_narrators). nullable.
 *
 * @param id surrogate UUID PK
 * @param slug стабильный идентификатор (bukhari / muslim / muwatta), UNIQUE
 * @param nameAr арабское название (обязательно)
 * @param nameEn английское название (nullable)
 * @param nameRu русское название (nullable)
 * @param compilerNarratorId nullable FK на составителя в hd_narrators
 * @param totalHadith число хадисов в сборнике (nullable, заполняется ETL)
 * @param metadata JSONB extensible
 * @param createdAt timestamp
 * @param bookId nullable FK на lib_books — мост к библиотечному представлению
 *        сборника (под-проект #3). Лениво заполняется
 *        {@code BookCollectionBridgeService}; null пока книга-представление
 *        ещё не создана
 */
public record Collection(
        UUID id,
        String slug,
        String nameAr,
        String nameEn,
        String nameRu,
        UUID compilerNarratorId,
        Integer totalHadith,
        String metadata,
        Instant createdAt,
        UUID bookId
) {
    /**
     * Backward-compat конструктор без {@code bookId} (9 аргументов) для
     * существующих call-site'ов (ETL-маппер, seeder, IT-фикстуры) — мост
     * заполняется отдельно через {@code BookCollectionBridgeService}, поэтому
     * при создании сборника bookId всегда null.
     */
    public Collection(
            UUID id, String slug, String nameAr, String nameEn, String nameRu,
            UUID compilerNarratorId, Integer totalHadith, String metadata,
            Instant createdAt
    ) {
        this(id, slug, nameAr, nameEn, nameRu, compilerNarratorId, totalHadith,
                metadata, createdAt, null);
    }
}
