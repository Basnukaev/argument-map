package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO для audit_log (Этап 22.d, ADR-043 Amendment 3).
 *
 * <p>{@code changes} - raw JSON-строка (формат зависит от {@code action}):
 * <ul>
 *   <li>CREATE: {@code {"created": {...snapshot}}}</li>
 *   <li>UPDATE: {@code {"field": {"old": X, "new": Y}}}</li>
 *   <li>DELETE: {@code {"deleted": {...snapshot}}}</li>
 *   <li>VISIBILITY_CHANGE: {@code {"visibility": {"old": X, "new": Y}}}</li>
 *   <li>MEMBER_ADD/REMOVE: {@code {"userId": "...", "role": "..."}}</li>
 *   <li>MEMBER_ROLE_CHANGE: {@code {"userId": "...", "role": {"old": X, "new": Y}}}</li>
 * </ul>
 * Frontend парсит как нужно по action - схему не валидируем (changes
 * это free-form jsonb).
 *
 * <p>{@code actorUsername} - JOIN с users.username. Если user удалён
 * (FK ON DELETE - сейчас RESTRICT, удалить user'а с audit записями
 * нельзя) - actorUsername всё равно есть. На случай повреждения данных:
 * null acceptable.
 */
public record AuditLogResponse(
        UUID id,
        @Schema(allowableValues = {
                "TOPIC", "NODE", "EDGE", "BOOK", "QUESTION", "ANSWER",
                "TOPIC_MEMBER", "BOOK_MEMBER",
                "NODE_SOURCE", "QUESTION_SOURCE", "ANSWER_SOURCE",
                "NODE_TRANSLATION"
        })
        String entityType,
        UUID entityId,
        @Schema(allowableValues = {
                "TOPIC", "NODE", "EDGE", "BOOK", "QUESTION", "ANSWER",
                "TOPIC_MEMBER", "BOOK_MEMBER",
                "NODE_SOURCE", "QUESTION_SOURCE", "ANSWER_SOURCE",
                "NODE_TRANSLATION"
        }, nullable = true)
        String parentEntityType,
        UUID parentEntityId,
        @Schema(allowableValues = {
                "CREATE", "UPDATE", "DELETE", "VISIBILITY_CHANGE",
                "MEMBER_ADD", "MEMBER_REMOVE", "MEMBER_ROLE_CHANGE"
        })
        String action,
        UUID actorUserId,
        String actorUsername,
        String changes,
        Instant createdAt
) {
}
