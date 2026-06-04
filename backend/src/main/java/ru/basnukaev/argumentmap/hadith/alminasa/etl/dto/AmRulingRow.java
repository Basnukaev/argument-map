package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_ruling}. Один док = один ruler × hadith. */
public record AmRulingRow(
        String esId,
        String hadithId,
        String ruler,
        Integer rulerDod,
        String narrationsType,
        String rawJson
) {
}
