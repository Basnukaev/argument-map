package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import java.util.regex.Pattern;

/**
 * Чистит «icon-font placeholder» символы из shamela HTML.
 *
 * <p><b>Контекст:</b> на сайте shamela.ws иконки (закладка/якорь возле
 * заголовка глав) реализованы через icon-font: в HTML стоит произвольный
 * CJK-кодпоинт (наблюдается {@code 舄} U+8204 - валидный китайский иероглиф),
 * который CSS подменяет глифом из шрифта {@code shamela-icons}. При парсинге
 * raw HTML без подключенного CSS - CJK-символы вылезают наружу как
 * посторонние знаки в арабском тексте.
 *
 * <p><b>Решение:</b> в арабской книге CJK-блоки Unicode не могут встречаться
 * легитимно, поэтому безопасно вырезаем их полностью. Регекс покрывает:
 * <ul>
 *   <li>{@code IsHan} - CJK Unified Ideographs (U+4E00-9FFF), а также
 *       Compatibility и Extensions через property-matching</li>
 *   <li>{@code IsHiragana} - японская хирагана (U+3040-309F)</li>
 *   <li>{@code IsKatakana} - японская катакана (U+30A0-30FF)</li>
 *   <li>{@code IsBopomofo} - тайваньская транскрипция (U+3100-312F)</li>
 *   <li>{@code IsHangul} - корейский (U+AC00-D7AF)</li>
 * </ul>
 *
 * <p><b>Что НЕ трогаем:</b> арабские presentation forms (U+FB50-FDFF,
 * U+FE70-FEFF) - там лежат сокращения вида {@code رحمه الله} (U+FDC0),
 * салават {@code ﷺ} (U+FDFA), {@code رضي الله عنه} (U+FDC9) и т.д.
 * Они смысловая часть текста и корректно рендерятся арабским шрифтом.
 * Регекс выше их не задевает (другой блок Unicode).
 *
 * <p><b>Совет (для будущих расширений):</b> если найдёте у shamela ещё
 * «иконочные» символы вне CJK (разные иконки могут сидеть на разных
 * кодпоинтах), запустите частотный словарь по `text_content` и фильтруйте
 * нелегитимные. Сейчас зафиксированы только CJK.
 */
public final class ShamelaTextCleaner {

    private static final Pattern CJK_NOISE = Pattern.compile(
            "[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsBopomofo}\\p{IsHangul}]"
    );

    private ShamelaTextCleaner() {
    }

    /**
     * Возвращает текст с удалёнными CJK-иконочными символами. Если
     * {@code raw} null или пустой - возвращает как есть.
     */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return CJK_NOISE.matcher(raw).replaceAll("");
    }
}
