package ru.basnukaev.argumentmap.qa.web.dto;

import jakarta.validation.constraints.Size;

import ru.basnukaev.argumentmap.qa.domain.QuestionStatus;

/**
 * Partial update вопроса. Все поля nullable - null = no change.
 */
public record UpdateQuestionRequest(
        @Size(max = 500)
        String title,
        @Size(max = 10000)
        String body,
        QuestionStatus status
) {
}
