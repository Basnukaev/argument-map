package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись audit_log (Этап 22.d, ADR-043 Amendment 3).
 *
 * <p>{@code changes} и {@code metadata} - сериализованный JSON-строкой
 * (хранится как jsonb в БД). Маппинг между Java Map / record →
 * JSON-строка делает {@link ru.basnukaev.argumentmap.service.AuditLogService}
 * через {@link com.fasterxml.jackson.databind.ObjectMapper}.
 *
 * <p>{@code parentEntityType} / {@code parentEntityId} - опциональные
 * (nullable) для child entities (node/edge → TOPIC parent). Топ-level
 * сущности (TOPIC, BOOK, QUESTION) parent не имеют.
 */
public record AuditLog(
        UUID id,
        String entityType,
        UUID entityId,
        String parentEntityType,
        UUID parentEntityId,
        String action,
        UUID actorUserId,
        String changes,
        String metadata,
        Instant createdAt
) {
}
