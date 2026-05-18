package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * POST /api/v1/nodes. {@code originalLang} опционален - язык оригинала
 * ('ar' | 'ru' | 'en'). Переводы добавляются отдельно через
 * POST /api/v1/nodes/{id}/translations (миграция 45).
 */
public record CreateNodeRequest(
        @NotNull UUID topicId,
        @NotNull NodeType nodeType,
        @NotBlank @Size(max = 10000) String content,
        @Pattern(regexp = "ar|ru|en", message = "originalLang должен быть 'ar', 'ru' либо 'en'")
        String originalLang
) {
}
