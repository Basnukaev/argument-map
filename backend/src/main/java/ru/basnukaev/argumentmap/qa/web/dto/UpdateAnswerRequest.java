package ru.basnukaev.argumentmap.qa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request на редактирование тела ответа (Этап 19.c, ADR-034). На MVP -
 * только body редактируется. Author и question_id неизменны.
 */
public record UpdateAnswerRequest(
        @NotBlank
        @Size(max = 10000)
        String body
) {
}
