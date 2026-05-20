package ru.basnukaev.argumentmap.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * PATCH /api/v1/users/{id}/role — Phase A.4 (Vision 49d). ADMIN-only
 * endpoint для повышения/понижения роли пользователя.
 *
 * <p>Семантическая валидация (whitelist) — в {@code UserRole.isValid}
 * на service-слое. Bean Validation тут — только not-blank guard
 * чтобы быстро отбросить пустой запрос на controller-уровне.
 */
public record ChangeRoleRequest(
        @NotBlank(message = "Поле newRole обязательно")
        @Schema(allowableValues = {"USER", "STUDENT", "SCHOLAR", "ADMIN"},
                description = "Новая роль пользователя - whitelist из 4 значений")
        String newRole
) {
}
