package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Источник цитат (Quran/Hadith/Book/Article/URL). DTO-стиль record - data
 * bag для JDBC row mapping. Type-predicates {@link #isHadith()} /
 * {@link #isBook()} используются в service-слое для user-facing валидации
 * (см. {@code HadithGradeService.addGrade}).
 */
public record Source(
        UUID id,
        SourceType sourceType,
        String title,
        String citation,
        Reliability reliability,
        UUID authorityId,
        UUID bookId,
        String metadata,
        Instant createdAt
) {

    public boolean isHadith() {
        return sourceType == SourceType.HADITH;
    }

    public boolean isBook() {
        return sourceType == SourceType.BOOK;
    }
}
