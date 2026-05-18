package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Повторная оценка от того же scholar'а на тот же хадис. Маппится в 409
 * hadith-grade-duplicate.
 */
public class HadithGradeDuplicateException extends RuntimeException {

    private final UUID sourceId;
    private final UUID scholarId;

    public HadithGradeDuplicateException(UUID sourceId, UUID scholarId) {
        super("Учёный %s уже оценил источник %s; используйте PATCH для изменения"
                .formatted(scholarId, sourceId));
        this.sourceId = sourceId;
        this.scholarId = scholarId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getScholarId() {
        return scholarId;
    }
}
