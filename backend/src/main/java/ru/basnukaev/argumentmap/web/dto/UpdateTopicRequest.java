package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Тело PATCH /api/v1/topics/{id} - partial update title/description темы.
 *
 * <p>PATCH-семантика per-field:
 * <ul>
 *   <li>{@code null} = поле не редактируется (no change)
 *   <li>non-null = заменить текущее значение
 * </ul>
 *
 * <p>Поле {@code description} разрешает пустую строку (clear description).
 * Поле {@code title} - non-null обязано пройти {@code @Size(min=1)} -
 * пустая строка отвергается (тема обязана иметь название).
 *
 * <p>Visibility / statusAlgorithm не меняются через этот endpoint - для
 * них есть отдельные PATCH'и (`/visibility`, `/status-algorithm`).
 */
public record UpdateTopicRequest(
        @Size(min = 1, max = 200, message = "title должен быть от 1 до 200 символов")
        String title,
        @Size(max = 2000, message = "description не длиннее 2000 символов")
        String description
) {
}
