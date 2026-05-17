package ru.basnukaev.argumentmap.library.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Член SHARED-книги (ADR-043 Amendment, Этап 22.c). Owner книги не
 * дублируется в этой таблице - он определяется через {@link Book#createdBy()}.
 * Аналог {@link ru.basnukaev.argumentmap.domain.TopicMember}.
 */
public record BookMember(
        UUID id,
        UUID bookId,
        UUID userId,
        String role,
        Instant addedAt,
        UUID addedBy
) {
}
