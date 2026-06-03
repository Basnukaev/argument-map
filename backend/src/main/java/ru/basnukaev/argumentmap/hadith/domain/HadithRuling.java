package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Импортированный вердикт учёного (alminasa rulings[]): свободный ruler +
 * год смерти. Ортогонально hadith_grades (ручные оценки юзеров через authorities).
 */
public record HadithRuling(
        UUID id,
        UUID hadithId,
        String rulerName,
        Integer rulerDeathYear,
        String rulingText,
        String bookName,
        Integer page,
        Integer volume,
        String metadata,
        Instant createdAt
) {
}
