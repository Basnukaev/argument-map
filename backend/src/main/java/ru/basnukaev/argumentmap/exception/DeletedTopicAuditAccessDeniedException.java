package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда non-ADMIN user пытается прочитать audit удалённой темы
 * через {@code GET /api/v1/audit/topics/{id}}. Бывший owner не должен
 * видеть историю удалённого ресурса (тема для него больше не существует),
 * но audit_log rows сохраняются для compliance forensics - смотреть может
 * только ADMIN.
 *
 * <p>Маппится в {@code 403 Forbidden} с Problem Details
 * {@code type: forbidden-deleted-topic-audit}. Backlog Tech debt round 3 #6
 * (закрыт 2026-05-19).
 */
public class DeletedTopicAuditAccessDeniedException extends RuntimeException {

    private final UUID topicId;
    private final UUID userId;

    public DeletedTopicAuditAccessDeniedException(UUID topicId, UUID userId) {
        super("Пользователь " + userId + " не имеет прав на audit удалённой темы " + topicId
                + " (только ADMIN)");
        this.topicId = topicId;
        this.userId = userId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public UUID getUserId() {
        return userId;
    }
}
