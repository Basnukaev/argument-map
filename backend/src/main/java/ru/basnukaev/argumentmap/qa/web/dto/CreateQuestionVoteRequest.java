package ru.basnukaev.argumentmap.qa.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Тело POST /api/v1/questions/{id}/vote. weight - обязательно, -1 либо +1.
 * Валидация диапазона - в Service (InvalidVoteException), здесь только
 * required.
 */
public record CreateQuestionVoteRequest(
        @NotNull Integer weight
) {
}
