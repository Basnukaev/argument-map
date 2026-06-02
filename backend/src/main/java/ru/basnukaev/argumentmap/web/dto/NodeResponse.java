package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * DTO узла. Голосование за узлы удалено - узлы это curated expert data,
 * голосование за них семантически неверно (см. ADR-053).
 * Community-сигнал популярности теперь на уровне тем (TopicResponse.voteScore).
 *
 * <p>zIndex - stacking order на канвасе. Default 0. Управляется через
 * POST /api/v1/nodes/{id}/z-order/bring-to-front и /send-to-back.
 *
 * <p>inlineCitations - список лёгких ссылок для inline-маркеров [N] в тексте.
 * Подход A (implicit ordinal) - frontend парсит `[1]`, `[2]` и находит ref
 * по 1-based ordinal. Bulk-load в GET /topics/{id}/graph (один SQL на весь
 * граф). Mutating endpoints (POST /nodes, PATCH /nodes/{id}) - точечная
 * подгрузка для одного узла. Пустой список если у узла нет node_sources
 *
 * <p>{@code originalLang} (миграция 44) - язык оригинала: 'ar' | 'ru' | 'en'
 * (null = фронт определит сам через hasArabicScript).
 *
 * <p>{@code translations} (миграция 45) - список переводов узла с attribution
 * переводчика (Кулиев, Sahih International, Османов и т.д.). Bulk-loaded
 * один SQL на весь граф на GET /topics/{id}/graph. Default-перевод первым,
 * затем по created_at ASC. Пустой список если переводов нет.
 */
public record NodeResponse(
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
        List<InlineCitationRef> inlineCitations,
        String originalLang,
        List<NodeTranslationRef> translations
) {
}
