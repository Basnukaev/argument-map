package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Книга/труд в библиотеке. {@code visibility} (ADR-043 Amendment, Этап 22.c):
 * PRIVATE (только owner), SHARED (owner + lib_book_members), PUBLIC (read для
 * всех authenticated, write только owner + EDITOR). Shamela ETL и старые
 * user-uploads имеют visibility=PUBLIC по умолчанию.
 */
public record Book(
        UUID id,
        BookType bookType,
        String title,
        UUID authorityId,
        String language,
        String description,
        String metadata,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        UUID muhaqqiqId,
        UUID publisherId,
        UUID publicationPlaceId,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian,
        String visibility
) {
}
