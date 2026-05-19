package ru.basnukaev.argumentmap.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Size;

/**
 * Partial-update payload для PATCH /api/v1/authorities/{id}.
 * Все поля опциональны (null = «не менять»). Поэтому нет @NotBlank -
 * в отличие от {@link CreateAuthorityRequest}, где name обязателен.
 * Если передать {@code name} с пустой строкой - сохранится пустая
 * строка (явный сброс); для «не трогать» нужно передать null / не
 * включать поле в JSON вовсе.
 *
 * <p>{@code type} валидируется по whitelist
 * {@link ru.basnukaev.argumentmap.domain.AuthorityType} на уровне
 * сервиса → 400 invalid-authority-type при некорректном значении.
 */
public record UpdateAuthorityRequest(
        @Size(max = 500) String name,
        @Size(max = 10000) String bio,
        @Size(max = 100) String era,
        @Size(max = 100) String madhab,
        JsonNode metadata,
        @Size(max = 20) String type
) {
}
