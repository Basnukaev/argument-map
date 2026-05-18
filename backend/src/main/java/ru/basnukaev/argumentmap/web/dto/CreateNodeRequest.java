package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * POST /api/v1/nodes. Bilingual поля (translation/translationLang/originalLang)
 * - опциональны. Если задан translation, translationLang обязателен
 * (валидация в NodeService).
 */
public record CreateNodeRequest(
        @NotNull UUID topicId,
        @NotNull NodeType nodeType,
        @NotBlank @Size(max = 10000) String content,
        @Size(max = 10000) String translation,
        @Pattern(regexp = "ru|en", message = "translationLang должен быть 'ru' либо 'en'")
        String translationLang,
        @Pattern(regexp = "ar|ru|en", message = "originalLang должен быть 'ar', 'ru' либо 'en'")
        String originalLang
) {
}
