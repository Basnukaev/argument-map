package ru.basnukaev.argumentmap.library.web.dto;

import java.util.UUID;

/**
 * Минимальный DTO мухаккика для autocomplete в BookEditModal (Этап 20.d).
 * Не включает {@code createdAt} - для UI не нужен.
 */
public record MuhaqqiqResponse(
        UUID id,
        String name,
        String fullName
) {
}
