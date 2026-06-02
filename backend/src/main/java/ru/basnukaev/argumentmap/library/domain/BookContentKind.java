package ru.basnukaev.argumentmap.library.domain;

/**
 * Ортогональная (к {@link BookType}) классификация доступности книги
 * (миграция 69). {@code book_type} отвечает на вопрос «какой это ЖАНР»
 * (QURAN/HADITH_COLLECTION/BOOK/...), а {@code content_kind} — «что
 * физически доступно для чтения».
 *
 * <ul>
 *   <li>{@code TEXT_ONLY}     — есть текст страниц, нет PDF-файла</li>
 *   <li>{@code TEXT_AND_FILE} — есть и текст, и файл</li>
 *   <li>{@code FILE_ONLY}     — есть файл, но текст ещё не извлечён (скан)</li>
 * </ul>
 */
public enum BookContentKind {
    TEXT_ONLY,
    TEXT_AND_FILE,
    FILE_ONLY;

    /**
     * Маппинг предиката доступности в {@code content_kind}. Зеркалит SQL
     * backfill в миграции 69 — единственный источник истины.
     *
     * <p>Случай «ни текста, ни файла» → {@code TEXT_ONLY} (безопасный
     * default): покрывает HADITH_COLLECTION-книги-мосты, которые
     * маршрутизируются в /hadith и никогда не открывают reader.
     */
    public static BookContentKind of(boolean hasText, boolean hasFile) {
        if (hasText && hasFile) {
            return TEXT_AND_FILE;
        }
        if (hasFile) {
            return FILE_ONLY;
        }
        return TEXT_ONLY;
    }
}
