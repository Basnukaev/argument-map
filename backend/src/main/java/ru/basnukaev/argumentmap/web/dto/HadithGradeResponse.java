package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.domain.HadithGradeValue;

/**
 * Read-response одной оценки хадиса. Содержит denormalized scholar info
 * (name / fullName / deathYearHijri) чтобы фронту не делать N запросов
 * на authorities. Маппится из {@code HadithGradeWithScholar}.
 */
public record HadithGradeResponse(
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
