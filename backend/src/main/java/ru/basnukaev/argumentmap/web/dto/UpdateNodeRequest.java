package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/nodes/{id}. Все поля опциональные - можно обновить
 * только содержимое (с записью revision), только координаты на канвасе
 * (без revision), либо то и другое сразу.
 */
public record UpdateNodeRequest(
        @Size(min = 1, max = 10000) String content,
        Double posX,
        Double posY
) {
}
