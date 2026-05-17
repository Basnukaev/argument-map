package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Тело POST /api/v1/nodes/{id}/vote. weight - обязательно, -1 либо +1.
 * Валидация диапазона - в Service (InvalidVoteException), здесь только
 * required.
 */
public record CreateNodeVoteRequest(
        @NotNull Integer weight
) {
}
