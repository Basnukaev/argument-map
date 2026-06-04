package ru.basnukaev.argumentmap.hadith.alminasa.etl.dto;

/** Строка {@code am_staging_hadith}: горячие поля + полный _source в raw. */
public record AmHadithRow(
        String hadithId,
        int bookId,
        long hadithSerialId,
        String bookName,
        String hadithType,
        String chapter,
        String subChapter,
        String rawJson
) {
}
