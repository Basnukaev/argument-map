package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Узел графа аргументации.
 *
 * <p>{@code originalLang} (миграция 44, оставшаяся после миграции 45) -
 * язык оригинала: 'ar' | 'ru' | 'en'. nullable. null означает auto-detect
 * на фронте через {@code hasArabicScript(content)}.
 *
 * <p>Переводы вынесены в child-сущность {@link NodeTranslation} (миграция
 * 45 - multi-translation 1:N). Один узел может иметь несколько переводов
 * от разных переводчиков (Кулиев, Sahih International, Османов и т.д.).
 */
public record Node(
        UUID id,
        UUID topicId,
        NodeType nodeType,
        String content,
        NodeStatus status,
        Double posX,
        Double posY,
        int zIndex,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        String originalLang
) {
}
