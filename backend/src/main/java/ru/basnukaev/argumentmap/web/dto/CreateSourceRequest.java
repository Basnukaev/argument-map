package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.Reliability;
import ru.basnukaev.argumentmap.domain.SourceType;

/**
 * Тело запроса POST /api/v1/sources. С Этапа 20.e добавлено опциональное
 * {@code bookId} - связывает Source с уже существующей записью Book для
 * structured citation (ADR-026). Без него Source считается freeform legacy.
 */
public record CreateSourceRequest(
        @NotNull SourceType sourceType,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 2000) String citation,
        Reliability reliability,
        UUID authorityId,
        UUID bookId,
        JsonNode metadata
) {
}
