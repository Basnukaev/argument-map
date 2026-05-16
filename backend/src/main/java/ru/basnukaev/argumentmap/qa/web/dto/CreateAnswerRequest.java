package ru.basnukaev.argumentmap.qa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request на создание ответа (Этап 19.c, ADR-034).
 *
 * @param body тело ответа, обязательно, до 10000 символов
 */
public record CreateAnswerRequest(
        @NotBlank
        @Size(max = 10000)
        String body
) {
}
