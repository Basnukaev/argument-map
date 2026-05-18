package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.NodeStatus;
import ru.basnukaev.argumentmap.domain.NodeType;

/**
 * DTO узла. Vote-поля (voteUpvotes/voteDownvotes/voteScore/userVote)
 * заполняются GET /api/v1/topics/{id}/graph и mutating endpoint'ами на
 * узлах. Для нагруженных bulk-операций - bulk-load из NodeVoteRepository,
 * один SQL на весь граф.
 *
 * <p>userVote ∈ {-1, +1, null}. null - вызывающий пользователь не голосовал.
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
 * <p>Bilingual поля (миграция 44, backlog Bilingual карточки):
 * <ul>
 *   <li>{@code translation} - перевод (nullable). null = перевода нет</li>
 *   <li>{@code translationLang} - язык перевода: 'ru' | 'en' (null если
 *       translation null)</li>
 *   <li>{@code originalLang} - язык оригинала: 'ar' | 'ru' | 'en' (null =
 *       фронт определит сам через hasArabicScript)</li>
 * </ul>
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
        int voteUpvotes,
        int voteDownvotes,
        int voteScore,
        Integer userVote,
        List<InlineCitationRef> inlineCitations,
        String translation,
        String translationLang,
        String originalLang
) {
}
