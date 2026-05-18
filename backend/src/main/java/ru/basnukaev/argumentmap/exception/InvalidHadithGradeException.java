package ru.basnukaev.argumentmap.exception;

/**
 * Невалидная оценка хадиса: попытка grade'нуть не-HADITH source,
 * unknown grade-значение либо иная ошибка payload. Маппится в 400
 * invalid-hadith-grade через GlobalExceptionHandler.
 */
public class InvalidHadithGradeException extends RuntimeException {

    public InvalidHadithGradeException(String message) {
        super(message);
    }
}
