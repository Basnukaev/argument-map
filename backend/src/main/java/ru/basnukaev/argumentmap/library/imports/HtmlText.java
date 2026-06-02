package ru.basnukaev.argumentmap.library.imports;

/**
 * Минимальный helper для снятия HTML-тегов из текста (ADR-056 amendment,
 * ADR-058). Используется там, где источник (archive.org / shamela) отдаёт
 * HTML-описание, а нам нужен plain text - для хранения в
 * {@code lib_books.description} (reader иначе показывает буквальные
 * {@code <div>} теги) либо для экономии токенов перед отправкой в LLM.
 *
 * <p>Намеренно простой regex (не полноценный HTML-парсер): {@code <br/>}
 * превращаем в перевод строки чтобы поля не слипались, остальные теги
 * удаляем, схлопываем горизонтальные пробелы.
 */
public final class HtmlText {

    private HtmlText() {
    }

    /**
     * Снять HTML-теги. {@code null}/blank → null. {@code <br/>} → перевод
     * строки, прочие теги → пробел, горизонтальные пробелы схлопываются.
     */
    public static String stripTags(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[ \\t]+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
