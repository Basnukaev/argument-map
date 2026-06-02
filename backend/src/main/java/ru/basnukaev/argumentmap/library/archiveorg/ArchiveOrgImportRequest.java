package ru.basnukaev.argumentmap.library.archiveorg;

import jakarta.validation.constraints.NotBlank;

/**
 * Тело {@code POST /api/v1/admin/archive-org/import} (ADR-056).
 *
 * <p>Подтверждённые админом «наши» поля (после gap-aware enrichment во
 * фронте) + выбор обложки. Группировка PDF в тома НЕ передаётся клиентом -
 * сервис заново детерминированно группирует по свежим metadata (single
 * source of truth, нельзя подделать список файлов). Текст из archive.org
 * не извлекается (FILE_ONLY, ADR-056 amendment b) - флагов извлечения нет.
 *
 * @param url             archive.org URL либо bare identifier (обязателен)
 * @param title           подтверждённый заголовок (null → берём из источника)
 * @param author          автор - имя для authority (опц.)
 * @param language        ISO-язык (null → из источника, иначе "ar")
 * @param description     описание (null → rawDescription источника)
 * @param muhaqqiqName    мухаккык - findOrCreate (опц.)
 * @param publisherName   издатель - findOrCreate (опц.)
 * @param placeName       место издания - findOrCreate (опц.)
 * @param editionNumber   номер издания (опц.)
 * @param yearHijri       год хиджры (опц.)
 * @param yearGregorian   год григорианский (опц.)
 * @param coverKind       выбранный вариант обложки:
 *                        {@code thumbnail}/{@code cover_pdf_page}/{@code upload}
 *                        (null → thumbnail по умолчанию)
 * @param coverUrl        явный URL обложки (для kind=upload; иначе сервис
 *                        выводит сам из identifier)
 */
public record ArchiveOrgImportRequest(
        @NotBlank String url,
        String title,
        String author,
        String language,
        String description,
        String muhaqqiqName,
        String publisherName,
        String placeName,
        Integer editionNumber,
        Integer yearHijri,
        Integer yearGregorian,
        String coverKind,
        String coverUrl
) {
}
