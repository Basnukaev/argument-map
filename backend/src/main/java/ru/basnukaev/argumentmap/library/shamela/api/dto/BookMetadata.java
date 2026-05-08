package ru.basnukaev.argumentmap.library.shamela.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ shamela на {@code GET /api/v1/patches/book-updates/{id}}.
 *
 * <p>Если у клиента {@code major_release}/{@code minor_release} устарели
 * относительно сервера, ответ содержит {@code major_release_url} - URL
 * полного snapshot новой major-версии книги. Для bootstrap (когда у
 * клиента ничего нет) шлём {@code major_release=0&minor_release=0} и
 * получаем URL последнего полного snapshot.
 *
 * <p>В случае когда у клиента актуальная major-версия и нужна только
 * minor-дельта, структура ответа другая (содержит {@code minor_release_url}
 * вместо {@code major_release_url}). MVP игнорирует patch-формат
 * (см. ADR-020), поэтому работаем только с {@code major_release_url}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookMetadata(
        @JsonProperty("major_release_url") String majorReleaseUrl,
        @JsonProperty("major_release") int majorRelease,
        @JsonProperty("minor_release") int minorRelease
) {
}
