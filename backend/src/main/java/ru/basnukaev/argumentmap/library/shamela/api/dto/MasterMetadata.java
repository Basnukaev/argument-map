package ru.basnukaev.argumentmap.library.shamela.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ shamela на {@code GET /api/v1/patches/master?version=N}.
 *
 * <p>{@code patchUrl} содержит уже-готовый URL для скачивания zip
 * с тремя SQLite (category/author/book). При {@code version=0} - полный
 * snapshot каталога, при {@code version=N} - дельта от N до latest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterMetadata(
        @JsonProperty("patch_url") String patchUrl,
        int version
) {
}
