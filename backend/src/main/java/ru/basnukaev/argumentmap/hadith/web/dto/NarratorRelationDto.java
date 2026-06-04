package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Связь в сети передатчиков (alminasa top_students/top_scholars).
 * {@code relatedNarratorId} nullable — заполнен, если рави уже импортирован
 * (резолв {@code relatedName} → наш FK). role ∈ {STUDENT, SCHOLAR}.
 */
public record NarratorRelationDto(
        UUID relatedNarratorId,
        String relatedName,
        String role,
        Integer cnt
) {
}
