package ru.basnukaev.argumentmap.hadith.sunnah.web.dto;

import ru.basnukaev.argumentmap.hadith.sunnah.service.SunnahMappingResult;

/**
 * Итог импорта сборника sunnah → hd_*. Phase 5 ETL шаг 2.d.
 */
public record SunnahImportResponse(
        String collectionName,
        int inserted,
        int skippedExisting,
        int skippedInvalid
) {
    public static SunnahImportResponse from(SunnahMappingResult r) {
        return new SunnahImportResponse(
                r.collectionName(), r.inserted(), r.skippedExisting(), r.skippedInvalid());
    }
}
