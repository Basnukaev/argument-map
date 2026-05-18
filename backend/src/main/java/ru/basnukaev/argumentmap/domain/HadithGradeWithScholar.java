package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Denormalized read-model для {@code GET /sources/{id}/grades} - содержит
 * scholar info в одной записи чтобы фронту не делать N запросов на
 * authorities. JOIN c authorities на стороне репозитория.
 */
public record HadithGradeWithScholar(
        UUID id,
        UUID sourceId,
        UUID scholarId,
        String scholarName,
        String scholarFullName,
        Integer scholarDeathYearHijri,
        HadithGradeValue grade,
        String gradeCitation,
        String comment,
        Instant createdAt,
        UUID createdBy
) {
}
