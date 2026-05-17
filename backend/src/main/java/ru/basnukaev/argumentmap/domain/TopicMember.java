package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Член SHARED-темы (ADR-043). Owner темы не дублируется в этой
 * таблице - он определяется через {@link Topic#createdBy()}.
 */
public record TopicMember(
        UUID id,
        UUID topicId,
        UUID userId,
        String role,
        Instant addedAt,
        UUID addedBy
) {
}
