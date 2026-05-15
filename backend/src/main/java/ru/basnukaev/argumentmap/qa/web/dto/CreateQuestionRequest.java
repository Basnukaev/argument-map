package ru.basnukaev.argumentmap.qa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request на создание вопроса (Этап 19.a, ADR-032).
 *
 * @param title заголовок вопроса, обязательный, до 500 символов
 * @param body  опциональное тело с подробностями (Markdown в будущем)
 */
public record CreateQuestionRequest(
        @NotBlank
        @Size(max = 500)
        String title,
        @Size(max = 10000)
        String body
) {
}
