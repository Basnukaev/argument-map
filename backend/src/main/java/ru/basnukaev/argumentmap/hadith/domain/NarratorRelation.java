package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Сеть передатчиков (alminasa top_students/top_scholars): имя + частота.
 * relatedNarratorId — резолв related_name в наш FK когда рави уже импортирован.
 * role ∈ {STUDENT, SCHOLAR}.
 */
public record NarratorRelation(
        UUID id,
        UUID narratorId,
        UUID relatedNarratorId,
        String relatedName,
        String role,
        Integer cnt,
        Instant createdAt
) {
}
