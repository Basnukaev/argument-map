package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Сборник хадисов для chip-фильтра/превью. {@code totalHadith} — заявленный
 * объём сборника (из источника), {@code hadithCount} — реально импортированных
 * в hd_hadiths (для дебага: видно сколько уже залито).
 */
public record CollectionResponse(
        UUID id,
        String slug,
        String nameAr,
        String nameEn,
        String nameRu,
        Integer totalHadith,
        long hadithCount
) {
}
