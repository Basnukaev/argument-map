package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Такхридж/طرق (alminasa crossref). {@code relatedHadithId} nullable —
 * заполнен, если сиблинг-предание уже импортировано (резолв
 * {@code relatedExternalId} → наш FK).
 */
public record CrossrefDto(
        String relatedExternalId,
        UUID relatedHadithId,
        String note
) {
}
