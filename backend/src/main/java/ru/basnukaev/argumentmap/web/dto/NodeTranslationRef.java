package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

/**
 * Лёгкая ссылка на перевод узла в {@link NodeResponse#translations()}.
 *
 * <p>{@code translatorName} - nullable (анонимный переводчик).
 * {@code language} ∈ {ru, en}. {@code isDefault} - какой перевод
 * показывать по умолчанию (один на узел).
 *
 * <p>Не содержит createdBy / createdAt / id parent-узла - в bulk-load
 * (Map<UUID, List<NodeTranslationRef>> на весь граф) экономим bytes.
 */
public record NodeTranslationRef(
        UUID id,
        String translatorName,
        String language,
        String body,
        boolean isDefault
) {
}
