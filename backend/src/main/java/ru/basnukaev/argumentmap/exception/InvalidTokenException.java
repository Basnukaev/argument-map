package ru.basnukaev.argumentmap.exception;

/**
 * JWT не прошёл валидацию: malformed / expired / signature mismatch /
 * unsupported algorithm. Возвращается 401 Unauthorized.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
