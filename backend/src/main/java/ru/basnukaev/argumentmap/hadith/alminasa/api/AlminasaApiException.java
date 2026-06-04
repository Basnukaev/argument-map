package ru.basnukaev.argumentmap.hadith.alminasa.api;

/**
 * Ошибка обращения к alminasa ES-прокси. {@code statusCode}: HTTP-статус
 * при не-2xx ответе; {@code 0} — I/O-ошибка (transient, ретраится);
 * {@code -1} — прерывание потока (НЕ ретраится).
 */
public class AlminasaApiException extends RuntimeException {

    private final int statusCode;

    public AlminasaApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AlminasaApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
