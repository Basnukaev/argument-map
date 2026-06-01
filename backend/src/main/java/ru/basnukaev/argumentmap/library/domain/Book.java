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
        String visibility,
        // Thesis metadata (миграция 58) - для академических рисала из
        // shamela. Nullable: обычные изданные книги их не имеют.
        // thesisDegree = ماجستير/دكتوراه, supervisor = إشراف, institution
        // = جامعة/كلية. Academic year маппится в publishedYearHijri.
        String thesisDegree,
        String thesisSupervisor,
        String thesisInstitution
) {
    /**
     * Backward-compat конструктор без thesis-полей (17 аргументов).
     * Большинство callers (не-shamela создание книги, IT-фикстуры) не
     * имеют thesis-данных - делегируем с null'ами, чтобы не править
     * десятки call-site'ов. Shamela mapper использует полный конструктор.
     */
    public Book(
            UUID id, BookType bookType, String title, UUID authorityId,
            String language, String description, String metadata, UUID createdBy,
            Instant createdAt, Instant updatedAt, UUID muhaqqiqId, UUID publisherId,
            UUID publicationPlaceId, Integer editionNumber, Integer publishedYearHijri,
            Integer publishedYearGregorian, String visibility
    ) {
        this(id, bookType, title, authorityId, language, description, metadata,
                createdBy, createdAt, updatedAt, muhaqqiqId, publisherId,
                publicationPlaceId, editionNumber, publishedYearHijri,
                publishedYearGregorian, visibility, null, null, null);
    }
}
