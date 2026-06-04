package ru.basnukaev.argumentmap.hadith.web.dto;

/** Печатное издание хадиса (alminasa editions[]). Секция «Издания» Hadith Explorer. */
public record EditionDto(
        String editionName,
        Integer page,
        Integer volume
) {
}
