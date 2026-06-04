package ru.basnukaev.argumentmap.hadith.web;

import java.util.UUID;

/**
 * Бросается когда у матна нет арабского текста (text_ar null/blank), а
 * значит переводить нечего (План 7, guard ДО LLM-вызова). Маппится в
 * 422 {@code invalid-matn-text} через GlobalExceptionHandler.
 */
public class InvalidMatnTextException extends RuntimeException {

    private final UUID matnId;

    public InvalidMatnTextException(UUID matnId) {
        super("У матна " + matnId + " пустой text_ar — нечего переводить");
        this.matnId = matnId;
    }

    public UUID getMatnId() {
        return matnId;
    }
}
