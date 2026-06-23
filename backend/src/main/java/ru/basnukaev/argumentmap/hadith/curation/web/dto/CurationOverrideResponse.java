package ru.basnukaev.argumentmap.hadith.curation.web.dto;

import java.time.Instant;
import java.util.UUID;

import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;

/**
 * Ответ курации (ADR-065): персистентная строка override. Возвращается
 * из PUT (upsert) и GET (список правок записи) для admin-вида.
 */
public record CurationOverrideResponse(
        UUID id,
        String entityTable,
        UUID entityId,
        String fieldName,
        String value,
        boolean isNull,
        boolean hidden,
        UUID editedBy,
        Instant editedAt,
        String reason
) {
    public static CurationOverrideResponse from(FieldOverride o) {
        return new CurationOverrideResponse(
                o.id(), o.entityTable(), o.entityId(), o.fieldName(),
                o.overrideValue(), o.isNullOverride(), o.hidden(),
                o.editedBy(), o.editedAt(), o.reason());
    }
}
