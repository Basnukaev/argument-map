package ru.basnukaev.argumentmap.hadith.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Narrator response DTO для GET endpoints. Vision 49d Section 2.6 Phase 1.
 *
 * <p>alminasa-обогащение: {@code tabaqa} (поколение), {@code gradeText}
 * (verbatim джарх-та'диль), {@code bornOnText}/{@code diedOnText} (проза
 * дат), {@code deathPlace}. {@code relations} (сеть передатчиков) и
 * {@code commentaries} (джарх/таʿдиль-цитаты учёных о рави, ADR-061) строятся
 * ТОЛЬКО в getOne (detail) — list-путь передаёт null (без N+1).
 */
public record NarratorResponse(
        UUID id,
        UUID authorityId,
        String nameAr,
        String kunya,
        String laqab,
        Integer yearBirthHijri,
        Integer yearDeathHijri,
        String birthplace,
        String primaryResidence,
        @Schema(allowableValues = {"THIQA", "SADUQ", "MAQBUL", "DAIF", "MATRUK", "SAHABI", "UNKNOWN"})
        String reliabilityGrade,
        String reliabilityComment,
        int transmittedCount,
        Instant createdAt,
        String tabaqa,
        String gradeText,
        String bornOnText,
        String diedOnText,
        String deathPlace,
        List<NarratorRelationDto> relations,
        List<NarratorCommentaryDto> commentaries
) {
}
