package ru.basnukaev.argumentmap.library.archiveorg;

/**
 * Переданный URL/identifier не распознан как archive.org-источник
 * (ADR-056). Маппится в {@code 400 Bad Request} - ошибка ввода
 * пользователя, а не технический сбой.
 */
public class InvalidArchiveOrgUrlException extends RuntimeException {

    public InvalidArchiveOrgUrlException(String message) {
        super(message);
    }
}
