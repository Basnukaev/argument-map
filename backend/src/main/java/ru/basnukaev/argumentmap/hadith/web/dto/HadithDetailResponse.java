package ru.basnukaev.argumentmap.hadith.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bundled hadith detail — hadith + sanads (with narrators in each) +
 * matns. Vision 49d Section 2.6 Phase 1.g.
 *
 * <p>Single payload primary endpoint для UI sanad graph viz.
 * Avoid N+1 на frontend (1 GET вместо 3+).
 */
public record HadithDetailResponse(
        UUID id,
        UUID collectionId,
        Integer primaryNumber,
        String normalizedMatn,
        String status,
        UUID sourceId,
        Instant createdAt,
        List<SanadDto> sanads,
        List<MatnDto> matns,
        List<GradeDto> grades
) {

    /** Курируемая оценка хадиса учёным (из hd_hadiths.metadata.grades). */
    public record GradeDto(
            String scholar,
            String grade,
            String note
    ) {
    }

    public record SanadDto(
            UUID id,
            String chainGrade,
            UUID compiledById,
            UUID compiledInBookId,
            boolean primaryChain,
            List<NarratorLinkDto> narrators
    ) {
    }

    public record NarratorLinkDto(
            int position,
            UUID narratorId,
            String transmissionPhrase
    ) {
    }

    public record MatnDto(
            UUID id,
            String textAr,
            String textRu,
            String textEn,
            UUID collectionId,
            Integer printedNumber,
            Integer pageNo,
            Integer volume,
            boolean isPrimary,
            String divergenceSummary
    ) {
    }
}
