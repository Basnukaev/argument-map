package ru.basnukaev.argumentmap.library.archiveorg;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Извлекает gap-поля библиографии из арабского HTML-{@code description}
 * archive.org-item'а (ADR-056). archive.org чисто отдаёт только title /
 * creator / language; издатель, год, число томов, издание чаще лежат
 * внутри арабского {@code description} в виде «метка: значение» строк
 * (подтверждено live-смоком {@code fmhji}). Этот парсер их вытягивает,
 * чтобы заполнить provenance-поля {@link ArchiveOrgPreview}.
 *
 * <p>Стиль зеркалит {@code ShamelaBibliographyParser}: консервативный
 * regex по arabic-меткам, {@code null} для отсутствующих/нечитаемых полей,
 * никогда не бросает. Разница: вход - HTML (теги {@code <br/>}, {@code
 * <div>} как разделители строк), а не shamela CR-separated текст.
 *
 * <h2>Поддерживаемые метки (значение после двоеточия, до конца строки)</h2>
 * <ul>
 *   <li>{@code المؤلف:} / {@code تأليف:} → author;</li>
 *   <li>{@code الناشر:} / {@code دار النشر:} → publisher (город внутри
 *       строки НЕ отделяем - кладём целиком, place остаётся missing -
 *       чистый split ненадёжен);</li>
 *   <li>{@code مكان النشر:} → place (явная метка - имеет приоритет);</li>
 *   <li>{@code سنة النشر:} / {@code عام النشر:} → год(ы). «1433 - 2012»
 *       → hijri=1433 (≤1600), gregorian=2012 (≥1800). Одно или оба;</li>
 *   <li>{@code عدد المجلدات:} → volumes (целое);</li>
 *   <li>{@code رقم الطبعة:} / {@code الطبعة:} → edition (arabic ordinal,
 *       best-effort: «الثالثة عشر»=13; если не парсится в int - null).</li>
 * </ul>
 */
@Component
public class ArchiveOrgDescriptionParser {

    // Граница «хиджра vs григориан»: годы ≤ HIJRI_MAX считаем хиджрой,
    // ≥ GREGORIAN_MIN - григорианским. Между ними - неоднозначно, пропускаем.
    private static final int HIJRI_MAX = 1600;
    private static final int GREGORIAN_MIN = 1800;

    private static final Pattern AUTHOR = labelPattern("(?:المؤلف|تأليف)");
    private static final Pattern PUBLISHER = labelPattern("(?:الناشر|دار النشر)");
    private static final Pattern PLACE = labelPattern("مكان النشر");
    private static final Pattern YEAR = labelPattern("(?:سنة النشر|عام النشر)");
    private static final Pattern VOLUMES = labelPattern("عدد المجلدات");
    private static final Pattern EDITION = labelPattern("(?:رقم الطبعة|الطبعة)");

    private static final Pattern NUMBER = Pattern.compile("([\\d٠-٩]+)");

    // Arabic ordinals 1-10 + единицы для составных «X عشر» (11-19).
    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("الأولى", 1),
            Map.entry("الاولى", 1),
            Map.entry("الثانية", 2),
            Map.entry("الثالثة", 3),
            Map.entry("الرابعة", 4),
            Map.entry("الخامسة", 5),
            Map.entry("السادسة", 6),
            Map.entry("السابعة", 7),
            Map.entry("الثامنة", 8),
            Map.entry("التاسعة", 9),
            Map.entry("العاشرة", 10)
    );

    /**
     * Распарсенные поля. {@code null} = метка не найдена / значение
     * нечитаемо. Никогда не бросает на любом входе.
     */
    public record ParsedDescription(
            String author,
            String publisher,
            String place,
            Integer editionNumber,
            Integer yearHijri,
            Integer yearGregorian,
            Integer volumes
    ) {
        static ParsedDescription empty() {
            return new ParsedDescription(null, null, null, null, null, null, null);
        }
    }

    public ParsedDescription parse(String descriptionHtml) {
        if (descriptionHtml == null || descriptionHtml.isBlank()) {
            return ParsedDescription.empty();
        }
        String text = stripHtml(descriptionHtml);

        String author = blank(extract(text, AUTHOR));
        String publisher = blank(extract(text, PUBLISHER));
        String place = blank(extract(text, PLACE));
        Integer volumes = parseInt(extract(text, VOLUMES));
        Integer edition = parseEdition(extract(text, EDITION));

        String yearLine = extract(text, YEAR);
        Integer hijri = null;
        Integer gregorian = null;
        if (yearLine != null) {
            Matcher m = NUMBER.matcher(yearLine);
            while (m.find()) {
                Integer year = parseIntRaw(m.group(1));
                if (year == null) {
                    continue;
                }
                if (year <= HIJRI_MAX && hijri == null) {
                    hijri = year;
                } else if (year >= GREGORIAN_MIN && gregorian == null) {
                    gregorian = year;
                }
            }
        }

        return new ParsedDescription(author, publisher, place, edition,
                hijri, gregorian, volumes);
    }

    // ---------------- HTML → plain lines ----------------

    /**
     * Превращает HTML в построчный plain-текст: блочные/{@code <br>}-теги →
     * перевод строки, остальные теги удаляются, минимальный decode entities.
     * Дальше парсинг идёт по строкам (метка в начале строки).
     */
    private static String stripHtml(String html) {
        String s = html
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)</\\s*(div|p|li|tr|h[1-6])\\s*>", "\n")
                .replaceAll("(?i)<\\s*(div|p|li|tr|h[1-6])(\\s[^>]*)?>", "\n")
                .replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        // схлопываем пробелы/табы внутри строки, но НЕ переводы строк
        return s.replaceAll("[ \\t\\u00A0]+", " ").trim();
    }

    /**
     * Метка в начале строки: {@code (^|\n) LABEL [пробелы] : значение} до
     * конца строки. Пробел перед двоеточием допустим ({@code عدد المجلدات :}).
     */
    private static Pattern labelPattern(String labelAlternation) {
        return Pattern.compile(
                "(?:^|\\n)\\s*" + labelAlternation + "\\s*:\\s*([^\\n]+)"
        );
    }

    private static String extract(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    // ---------------- value parsing ----------------

    /** Целое из значения (берёт первое число, поддерживает arabic-indic). */
    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(value);
        return m.find() ? parseIntRaw(m.group(1)) : null;
    }

    private static Integer parseIntRaw(String digits) {
        if (digits == null) {
            return null;
        }
        try {
            return Integer.parseInt(arabicIndicToAscii(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Номер издания. Сначала ASCII/arabic-indic цифры, иначе arabic ordinal.
     * Составные «X عشر» (11-19): ordinal единиц + 10. «الثالثة عشر» = 3+10=13.
     * Не распарсилось - {@code null} (edition остаётся missing).
     */
    private static Integer parseEdition(String value) {
        if (value == null) {
            return null;
        }
        Matcher digits = NUMBER.matcher(value);
        if (digits.find()) {
            Integer n = parseIntRaw(digits.group(1));
            if (n != null && n >= 1 && n <= 99) {
                return n;
            }
        }
        boolean hasTeen = value.contains("عشر"); // «... عشر» → +10
        Integer base = null;
        for (var e : ORDINALS.entrySet()) {
            if (value.contains(e.getKey())) {
                base = e.getValue();
                break;
            }
        }
        if (base == null) {
            return null;
        }
        return hasTeen ? base + 10 : base;
    }

    private static String arabicIndicToAscii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + (c - '٠')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String blank(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
