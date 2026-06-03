package ru.basnukaev.argumentmap.hadith.domain;

import java.util.UUID;

/** Печатное издание хадиса (alminasa editions[]): edition/page/volume. */
public record HadithEdition(
        UUID id,
        UUID hadithId,
        String editionName,
        Integer page,
        Integer volume
) {
}
