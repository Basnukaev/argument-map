package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Источник цитат (Quran/Hadith/Book/Article/URL). DTO-стиль record - data
 * bag для JDBC row mapping. Бизнес-инварианты («только HADITH принимает
 * grades», «BOOK source требует non-null bookId») вынесены в guard-методы
 * чтобы не дублировать в сервисах / репозиториях.
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

    /**
     * Только BOOK source имеет non-null bookId (link на library entity).
     * Quran/Hadith/Article/URL источники - без bookId. Инвариант проверяется
     * репозиторием через unique index (source_type, book_id) + CHECK
     * constraint; этот predicate для service-level guards до INSERT.
     */
    public boolean requiresBookLink() {
        return isBook();
    }

    /**
     * Guard для service / repository call sites которые работают только с
     * определённым типом source'а. Бросает {@link IllegalStateException}
     * с подробной диагностикой если ожидание не совпало - заменяет
     * inline {@code if (source.sourceType() != ...) throw ...} pattern
     * (audit 2026-05-18 finding 3).
     *
     * <p>Используем {@link IllegalStateException} а не доменный exception
     * - это programming error, не user-facing validation. Должен быть
     * пойман в раунде кода review / тестах. User-facing валидация (типа
     * «grade принимает только хадис») остаётся на service-слое через
     * specific exception (InvalidHadithGradeException и т.п.).
     */
    public void requireType(SourceType expected) {
        if (sourceType != expected) {
            throw new IllegalStateException(
                    "Source " + id + ": expected sourceType=" + expected
                            + ", got " + sourceType);
        }
    }
}
