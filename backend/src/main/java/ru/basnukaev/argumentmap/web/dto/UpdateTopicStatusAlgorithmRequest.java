package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Тело PATCH /api/v1/topics/{id}/status-algorithm (ADR-044). Включает
 * валидацию whitelist прямо в DTO - Pattern regex даёт 400
 * bad-request если значение вне списка ещё до Service-слоя
 */
public record UpdateTopicStatusAlgorithmRequest(
        @NotNull
        @Pattern(regexp = "MVP|DUNG_GROUNDED",
                message = "algorithm должен быть MVP или DUNG_GROUNDED")
        String algorithm
) {
}
