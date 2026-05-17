package ru.basnukaev.argumentmap.exception;

/**
 * Невалидный голос за узел: weight не из {-1, +1}. Маппится в 400
 * invalid-vote через GlobalExceptionHandler.
 */
public class InvalidVoteException extends RuntimeException {

    public InvalidVoteException(String message) {
        super(message);
    }
}
