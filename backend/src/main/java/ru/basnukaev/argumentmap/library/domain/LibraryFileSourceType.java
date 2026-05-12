package ru.basnukaev.argumentmap.library.domain;

/**
 * Дискриминатор происхождения бинарного файла в object storage.
 * Соответствует CHECK constraint на {@code library_files.source_type}
 * (миграция 21, ADR-024).
 */
public enum LibraryFileSourceType {
    SHAMELA,
    ARCHIVE_ORG,
    USER_UPLOAD,
    SCAN,
    DERIVED
}
