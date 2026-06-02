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
        String thesisInstitution,
        // Ссылка на обложку (миграция 67, ADR-056) - archive.org thumbnail /
        // первая страница cover-PDF / загруженная картинка. Nullable:
        // shamela ETL и старые user-uploads обложки не имеют (фронт
        // показывает letter-avatar). Заполняется archive.org-импортом.
        String coverUrl,
        // Availability-классификация (миграция 69) - ортогональна bookType
        // (жанр). NOT NULL в БД с DEFAULT TEXT_ONLY. createBook ставит
        // провизорный TEXT_ONLY, импортёры уточняют через
        // updateContentKind после записи страниц/файлов.
        BookContentKind contentKind
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
                publishedYearGregorian, visibility, null, null, null, null,
                BookContentKind.TEXT_ONLY);
    }

    /**
     * Backward-compat конструктор без coverUrl (20 аргументов) - shamela
     * mapper и прочие callers создавшие книгу до миграции 67 не передают
     * обложку (она ставится позже отдельным {@code updateCoverUrl} только
     * archive.org-импортом). Делегируем с {@code coverUrl=null}.
     */
    public Book(
            UUID id, BookType bookType, String title, UUID authorityId,
            String language, String description, String metadata, UUID createdBy,
            Instant createdAt, Instant updatedAt, UUID muhaqqiqId, UUID publisherId,
            UUID publicationPlaceId, Integer editionNumber, Integer publishedYearHijri,
            Integer publishedYearGregorian, String visibility,
            String thesisDegree, String thesisSupervisor, String thesisInstitution
    ) {
        this(id, bookType, title, authorityId, language, description, metadata,
                createdBy, createdAt, updatedAt, muhaqqiqId, publisherId,
                publicationPlaceId, editionNumber, publishedYearHijri,
                publishedYearGregorian, visibility,
                thesisDegree, thesisSupervisor, thesisInstitution, null,
                BookContentKind.TEXT_ONLY);
    }
}
