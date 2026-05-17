package ru.basnukaev.argumentmap.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Регистрация нового пользователя (ADR-040). Валидация:
 * email формата RFC, username 3..50 ASCII + цифры + _-,
 * password минимум 8 символов.
 */
public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                message = "username допускает только латинские буквы, цифры, _ и -")
        String username,

        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}
