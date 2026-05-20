package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Цепь передачи (sanad) хадиса. Vision 49d Section 2.6 Phase 1.
 * Mirror schema hd_sanads.
 */
public record Sanad(
        UUID id,
        UUID hadithId,
        String chainGrade,
        UUID compiledById,
        UUID compiledInBookId,
        boolean primaryChain,
        String metadata,
        Instant createdAt
) {
}
