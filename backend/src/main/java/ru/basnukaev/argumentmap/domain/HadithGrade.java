package ru.basnukaev.argumentmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Оценка хадиса конкретным учёным. Один хадис ({@code Source} с
 * {@code sourceType=HADITH}) может иметь много grade'ов от разных
 * учёных (Бухари: SAHIH, Тирмизи: HASAN и т.д.).
 *
 * <p>Уникальность: один scholar - одна оценка для конкретного source.
 * Повторное добавление выкидывает 409.
 */
public record HadithGrade(
        UUID id,
        UUID sourceId,
        UUID scholarId,
        HadithGradeValue grade,
        String gradeCitation,
        String comment,
        Instant createdAt,
        UUID createdBy
) {
}
