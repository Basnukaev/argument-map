package ru.basnukaev.argumentmap.exception;

/**
 * Унифицированная ошибка login: неверный email, неверный password,
 * disabled аккаунт. Намеренно один тип чтобы не leak'ать что именно
 * пошло не так (по timing / message). Возвращается 401 Unauthorized.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
