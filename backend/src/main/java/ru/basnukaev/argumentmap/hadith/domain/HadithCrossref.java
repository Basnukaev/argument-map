package ru.basnukaev.argumentmap.hadith.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Такхридж/طرق (alminasa raw_narrations): связь хадиса с сиблинг-преданием.
 * relatedHadithId — резолв relatedExternalId в наш FK когда сиблинг уже импортирован.
 */
public record HadithCrossref(
        UUID id,
        UUID hadithId,
        String relatedExternalId,
        UUID relatedHadithId,
        String relationType,
        String note,
        Instant createdAt
) {
}
