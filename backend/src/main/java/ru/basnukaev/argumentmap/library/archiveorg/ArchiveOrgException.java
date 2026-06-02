package ru.basnukaev.argumentmap.library.archiveorg;

/**
 * Ошибка взаимодействия с archive.org: не-2xx HTTP, проблемы сети,
 * битый JSON, прерванный поток, открытый circuit breaker (ADR-056).
 * Маппится в {@code 502 Bad Gateway} - archive.org это внешний
 * интеграционный канал, недоступность которого не вина клиента.
 *
 * <p>404 от archive.org (item не найден) оборачивается в отдельный
 * {@link ArchiveOrgItemNotFoundException} → 404.
 */
public class ArchiveOrgException extends RuntimeException {

    public ArchiveOrgException(String message) {
        super(message);
    }

    public ArchiveOrgException(String message, Throwable cause) {
        super(message, cause);
    }
}
