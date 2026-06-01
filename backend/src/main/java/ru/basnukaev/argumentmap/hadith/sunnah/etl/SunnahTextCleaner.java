package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Чистка текста дампа sunnah.com от inline-разметки (Phase 5).
 *
 * <p>Реальный matn содержит: HTML-теги (`&lt;p&gt;`/`&lt;br&gt;`), quran-якоря
 * `&lt;A href="javascript:openquran(5,82,82)"&gt;…аят…&lt;/A&gt;`, footnote-
 * маркеры `&lt;c_qNN&gt;…&lt;/c_qNN&gt;`, `&lt;a/l/&gt;`. Метод срезает ВСЕ теги
 * (inner-текст — например текст аята — сохраняется), декодирует HTML-entities
 * и схлопывает пробелы.
 *
 * <p>Применяется в {@link SunnahDumpReader} к арабскому/английскому тексту до
 * записи в staging → {@code hd_matns}; нормализованный matn считается уже от
 * чистого текста.
 */
public final class SunnahTextCleaner {

    private SunnahTextCleaner() {
    }

    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        // теги → пробел (чтобы соседние слова без пробела не склеились),
        // затем decode HTML-entities, затем схлопывание пробелов
        String noTags = input.replaceAll("<[^>]*>", " ");
        String decoded = StringEscapeUtils.unescapeHtml4(noTags);
        return decoded.replaceAll("\\s+", " ").trim();
    }
}
