package ru.basnukaev.argumentmap.library.imports;

import java.util.UUID;

/**
 * Опциональные поля метаданных при загрузке PDF/EPUB пользователем через
 * {@code POST /api/v1/library/imports/file} (Этап 16.b). Все поля nullable -
 * если не заданы, {@link FileImportService} либо подставляет дефолт
 * (например {@code language="ar"}), либо пытается извлечь значение из
 * самого PDF (например {@code title} через {@code PDDocumentInformation}).
 *
 * <p>Этап 16.g - расширено 6 academic-полями (мухаккик / издатель / место
 * издания / номер издания / годы хиджра/григориан). Если хотя бы одно
 * заполнено, {@link FileImportService} использует 13-args перегрузку
 * {@code BookService.createBook} которая выполнит findOrCreate в
 * справочниках. Иначе - старая 7-args перегрузка без academic FK.
 *
 * @param title заголовок книги, override автоматически извлечённого
 *              из PDF metadata. Если оба null - используется
 *              {@code filename} без расширения
 * @param authorityId UUID существующего {@code Authority} - связывает
 *                    книгу с автором. Валидируется в
 *                    {@link ru.basnukaev.argumentmap.library.service.BookService}
 * @param language ISO 639-1 (например {@code "ar"} или {@code "ru"}).
 *                 Default - {@code "ar"} (платформа в первую очередь
 *                 арабоязычная)
 * @param description свободный текст, заметки о книге
 * @param muhaqqiqName имя мухаккика (محقق - редактора тахкика).
 *                     findOrCreate по trimmed имени в {@code lib_muhaqqiqs}
 * @param publisherName имя издателя. findOrCreate в {@code lib_publishers}
 * @param publicationPlaceName город/страна издания. findOrCreate в
 *                             {@code lib_publication_places}
 * @param editionNumber номер издания (1..99)
 * @param publishedYearHijri год издания по хиджре (1..9999)
 * @param publishedYearGregorian год издания по григориану (1..9999)
 */
public record ImportMetadata(
        String title,
        UUID authorityId,
        String language,
        String description,
        String muhaqqiqName,
        String publisherName,
        String publicationPlaceName,
        Integer editionNumber,
        Integer publishedYearHijri,
        Integer publishedYearGregorian
) {
    public ImportMetadata(String title, UUID authorityId, String language, String description) {
        this(title, authorityId, language, description, null, null, null, null, null, null);
    }

    public static ImportMetadata empty() {
        return new ImportMetadata(null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Хотя бы одно academic поле заполнено - сигнал для
     * {@link FileImportService} использовать 13-args перегрузку
     * {@code BookService.createBook} с findOrCreate FK. Иначе FK остаются
     * null и используется быстрый shamela-совместимый путь (7 args).
     */
    public boolean hasAcademicData() {
        return isNonBlank(muhaqqiqName)
                || isNonBlank(publisherName)
                || isNonBlank(publicationPlaceName)
                || editionNumber != null
                || publishedYearHijri != null
                || publishedYearGregorian != null;
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
