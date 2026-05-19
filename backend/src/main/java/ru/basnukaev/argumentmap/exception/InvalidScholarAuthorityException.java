package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Попытка использовать Authority с типом отличным от
 * {@link ru.basnukaev.argumentmap.domain.AuthorityType#SCHOLAR} в качестве
 * scholar при добавлении оценки хадиса. Семантически оценивать хадис как
 * sahih/hasan/daif/maudu может только учёный (muhaddith), не издательство
 * или тахкик. Маппится в 400 invalid-scholar-authority через
 * GlobalExceptionHandler.
 */
public class InvalidScholarAuthorityException extends RuntimeException {

    private final UUID authorityId;
    private final String actualType;

    public InvalidScholarAuthorityException(UUID authorityId, String actualType) {
        super("Authority %s имеет тип '%s', ожидается SCHOLAR для оценки хадиса"
                .formatted(authorityId, actualType));
        this.authorityId = authorityId;
        this.actualType = actualType;
    }

    public UUID getAuthorityId() {
        return authorityId;
    }

    public String getActualType() {
        return actualType;
    }
}
