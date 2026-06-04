package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Narrator (рāwī) в иснадах хадисов. Vision 49d Section 2.6 Phase 1.
 *
 * <p>Cross-link authorityId nullable → authorities(id) - для известных
 * учёных мостимся на existing Authority. Например Бухари как Authority
 * с типом SCHOLAR + Narrator передаёт хадис.
 *
 * <p>reliability_grade whitelist (CHECK в БД): THIQA/SADUQ/MAQBUL/DAIF/
 * MATRUK/UNKNOWN. Constants — {@link NarratorReliability}.
 *
 * <p>nameArNormalized - для search/disambiguation (диакритика убрана,
 * лекtter normalization). Заполняется в service-слое на save.
 *
 * <p>transmittedCountCached - denormalized counter для UX side-panel
 * "сколько хадисов передал". Backfill через janitor.
 */
public record Narrator(
        UUID id,
        UUID authorityId,
        String nameAr,
        String nameArNormalized,
        String kunya,
        String laqab,
        Integer yearBirthHijri,
        Integer yearDeathHijri,
        String birthplace,
        String deathPlace,
        String primaryResidence,
        String reliabilityGrade,
        String reliabilityComment,
        int transmittedCountCached,
        String metadata,
        Instant createdAt,
        String externalSource,
        String externalId,
        String tabaqa,
        String gradeText,
        String bornOnText,
        String diedOnText
) {
    /**
     * Backward-compat конструктор без alminasa-полей (16 аргументов) для
     * существующих call-site'ов (DevHadithSeeder, IT-фикстуры).
     * alminasa-импортёр использует полный конструктор.
     */
    public Narrator(
            UUID id, UUID authorityId, String nameAr, String nameArNormalized,
            String kunya, String laqab, Integer yearBirthHijri, Integer yearDeathHijri,
            String birthplace, String deathPlace, String primaryResidence,
            String reliabilityGrade, String reliabilityComment, int transmittedCountCached,
            String metadata, Instant createdAt
    ) {
        this(id, authorityId, nameAr, nameArNormalized, kunya, laqab,
                yearBirthHijri, yearDeathHijri, birthplace, deathPlace, primaryResidence,
                reliabilityGrade, reliabilityComment, transmittedCountCached, metadata,
                createdAt, null, null, null, null, null, null);
    }
}
