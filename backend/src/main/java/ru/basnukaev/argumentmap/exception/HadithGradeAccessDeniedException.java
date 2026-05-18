package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Попытка update/delete чужой оценки без ADMIN-роли. 403
 * forbidden-hadith-grade-write.
 */
public class HadithGradeAccessDeniedException extends RuntimeException {

    private final UUID gradeId;
    private final UUID userId;

    public HadithGradeAccessDeniedException(UUID gradeId, UUID userId) {
        super("User %s не может изменять оценку %s (только автор или ADMIN)"
                .formatted(userId, gradeId));
        this.gradeId = gradeId;
        this.userId = userId;
    }

    public UUID getGradeId() {
        return gradeId;
    }

    public UUID getUserId() {
        return userId;
    }
}
