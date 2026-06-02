package ru.basnukaev.argumentmap.library.archiveorg;

/**
 * archive.org вернул для identifier пустой metadata (item не существует
 * либо удалён). Маппится в {@code 404 Not Found} (ADR-056) - в отличие
 * от {@link ArchiveOrgException} (502, технический сбой канала).
 */
public class ArchiveOrgItemNotFoundException extends RuntimeException {

    public ArchiveOrgItemNotFoundException(String identifier) {
        super("archive.org item не найден: " + identifier);
    }
}
