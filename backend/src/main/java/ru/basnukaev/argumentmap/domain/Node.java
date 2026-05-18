package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Узел графа аргументации.
 *
 * <p>Bilingual поля (миграция 44):
 * <ul>
 *   <li>{@code translation} - текст перевода (nullable). null = перевода нет</li>
 *   <li>{@code translationLang} - язык перевода: 'ru' | 'en'. Должен быть
 *       NOT NULL когда translation NOT NULL (валидация в NodeService)</li>
 *   <li>{@code originalLang} - язык оригинала: 'ar' | 'ru' | 'en'. nullable.
 *       null означает auto-detect на фронте через hasArabicScript(content)</li>
 * </ul>
 *
 * <p>MVP: один перевод на узел. Multi-translation (разные переводчики одного
 * аята) - в backlog.
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
        String translation,
        String translationLang,
        String originalLang
) {
}
