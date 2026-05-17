package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
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
 */
public record NodeResponse(
        UUID id,
        UUID topicId,
        NodeType nodeType,
        String content,
        NodeStatus status,
        Double posX,
        Double posY,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        int voteUpvotes,
        int voteDownvotes,
        int voteScore,
        Integer userVote
) {
}
