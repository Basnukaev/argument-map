package ru.basnukaev.argumentmap.exception;

/**
 * Передан невалидный {@code type} для Authority (не входит в whitelist
 * {@link ru.basnukaev.argumentmap.domain.AuthorityType}). Маппится в
 * 400 invalid-authority-type через GlobalExceptionHandler.
 */
public class InvalidAuthorityTypeException extends RuntimeException {

    private final String invalidType;

    public InvalidAuthorityTypeException(String invalidType) {
        super("Невалидный тип authority: '%s'. Допустимые: SCHOLAR, MUHAQQIQ, PUBLISHER, AUTHOR, OTHER"
                .formatted(invalidType));
        this.invalidType = invalidType;
    }

    public String getInvalidType() {
        return invalidType;
    }
}
