package ru.basnukaev.argumentmap.library.shamela.etl.dto;

/**
 * Строка из {@code book.sqlite} master-архива shamela.
 *
 * <p>shamela хранит большинство полей как TEXT с возможностью пустых
 * строк и магических значений ("99999" для неизвестного года). После
 * {@link ru.basnukaev.argumentmap.library.shamela.etl.SqliteValueParser}
 * - типизированные nullable-поля.
 *
 * <p>{@code pdfLinksJson} и {@code extraMetadataJson} - сырые JSON-строки
 * из соответствующих полей shamela. DAO при upsert оборачивает их
 * в {@code PGobject(jsonb)} - не парсим в JsonNode на этапе чтения,
 * не зачем (jsonb на postgres-стороне валидирует).
 */
public record ShamelaBookRow(
        long id,
        String name,
        Long categoryId,
        Long authorId,
        Integer type,
        Integer publicationYear,
        Boolean isPrinted,
        int majorRelease,
        int minorRelease,
        String bibliography,
        String hint,
        String pdfLinksJson,
        String extraMetadataJson,
        boolean deleted
) {
}
