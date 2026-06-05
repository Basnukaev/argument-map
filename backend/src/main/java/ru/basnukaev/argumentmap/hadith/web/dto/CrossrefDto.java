package ru.basnukaev.argumentmap.hadith.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Такхридж/طرق (alminasa crossref). {@code relatedHadithId} nullable —
 * заполнен, если сиблинг-предание уже импортировано (резолв
 * {@code relatedExternalId} → наш FK).
 *
 * <p>{@code numbers} — номера сиблинга в его сборнике (распарсенный
 * JSON-массив из {@code hd_hadith_crossrefs.note}; битый/пустой → пустой
 * список). {@code collectionNameAr}/{@code collectionNameRu} — название
 * сборника сиблинга по префиксу {@code relatedExternalId} ({@code bookId-…})
 * через {@link ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaCollections};
 * непарсится/неизвестный сборник → null (юзеру показываем человекочитаемый
 * сборник вместо сырого id).
 */
public record CrossrefDto(
        String relatedExternalId,
        UUID relatedHadithId,
        List<String> numbers,
        String collectionNameAr,
        String collectionNameRu
) {
}
