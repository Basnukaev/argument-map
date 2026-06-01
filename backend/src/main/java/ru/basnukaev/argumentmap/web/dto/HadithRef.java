package ru.basnukaev.argumentmap.web.dto;

import java.util.UUID;

/**
 * Хадис-специфичные поля для source-опоры типа HADITH (под-проект #2).
 * Не-null только когда source — мост хадиса ({@code hd_hadiths.source_id}).
 * Позволяет frontend'у отрендерить хадис-карточку (matn + сборник + статус)
 * без дополнительного запроса.
 *
 * @param hadithId       id хадиса в hd_hadiths
 * @param primaryNumber  номер в сборнике (nullable)
 * @param collectionName человекочитаемое имя сборника (nameRu→nameAr→slug)
 * @param previewMatn    диакритизированный text_ar первичного matn (nullable)
 * @param status         статус хадиса (CANONICAL/VARIANT/…) для бейджа
 */
public record HadithRef(
        UUID hadithId,
        Integer primaryNumber,
        String collectionName,
        String previewMatn,
        String status
) {
}
