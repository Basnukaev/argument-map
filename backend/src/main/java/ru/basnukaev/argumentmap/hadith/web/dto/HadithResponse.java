package ru.basnukaev.argumentmap.hadith.web.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import ru.basnukaev.argumentmap.hadith.domain.Hadith;

/**
 * Hadith response DTO для GET endpoints (list + single).
 * Phase 1.f - thin response (без sanads/matns - они на отдельных
 * endpoints либо в bundled detail GET позже).
 */
public record HadithResponse(
        UUID id,
        UUID collectionId,
        Integer primaryNumber,
        String normalizedMatn,
        @Schema(allowableValues = {"CANONICAL", "VARIANT", "WEAK", "FABRICATED"})
        String status,
        UUID sourceId,
        Instant createdAt
) {

    public static HadithResponse from(Hadith h) {
        return new HadithResponse(h.id(), h.collectionId(), h.primaryNumber(),
                h.normalizedMatn(), h.status(), h.sourceId(), h.createdAt());
    }
}
