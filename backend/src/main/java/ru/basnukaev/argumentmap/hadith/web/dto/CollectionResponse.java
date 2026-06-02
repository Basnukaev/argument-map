package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.UUID;

/**
 * Сборник хадисов для chip-фильтра/превью. {@code totalHadith} — заявленный
 * объём сборника (из источника), {@code hadithCount} — реально импортированных
 * в hd_hadiths (для дебага: видно сколько уже залито). {@code bookId} —
 * мост к библиотечному представлению сборника (под-проект #3): nullable, фронт
 * по нему даёт ссылку «открыть в библиотеке».
 */
public record CollectionResponse(
        UUID id,
        String slug,
        String nameAr,
        String nameEn,
        String nameRu,
        Integer totalHadith,
        long hadithCount,
        UUID bookId
) {
}
