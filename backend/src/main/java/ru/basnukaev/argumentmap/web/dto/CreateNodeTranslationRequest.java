package ru.basnukaev.argumentmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/nodes/{nodeId}/translations.
 *
 * <p>{@code translatorName} - optional (null/blank = анонимный переводчик).
 * {@code language} обязателен и ∈ {ru, en}. {@code body} обязателен non-blank.
 * {@code isDefault} - optional, default false. Если узел не имеет ни одного
 * перевода, сервис сделает первый перевод default'ом независимо от флага.
 */
public record CreateNodeTranslationRequest(
        @Size(max = 200) String translatorName,
        @NotNull @Pattern(regexp = "ru|en", message = "language должен быть 'ru' либо 'en'")
        String language,
        @NotBlank @Size(max = 10000) String body,
        Boolean isDefault
) {
}
