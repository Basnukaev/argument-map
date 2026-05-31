package ru.basnukaev.argumentmap.hadith.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Narrator response DTO для GET endpoints. Vision 49d Section 2.6 Phase 1.
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
        Instant createdAt
) {
}
