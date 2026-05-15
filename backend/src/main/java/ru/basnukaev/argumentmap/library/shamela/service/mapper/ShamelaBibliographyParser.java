package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Извлекает academic citation metadata из неструктурированного arabic-текста
 * shamela bibliography (поле {@code lib_shamela_book.bibliography}). Этап 20.c.
 *
 * <p>Shamela хранит bibliography как один TEXT с разделителем {@code "\r"}
 * (literal backslash + r, не carriage return) между полями. Каждое поле имеет
 * arabic marker и текст после двоеточия. Парсер консервативен - возвращает
 * {@code null} для каждого поля если marker не найден или значение пустое.
 *
 * <p>Поддерживаемые markers:
 * <ul>
 *   <li>{@code المحقق:} - мухаккик. Альтернативный вариант
 *       {@code حققه ووضع حواشيه:} тоже распознаётся (встречается в
 *       академических изданиях)</li>
 *   <li>{@code الناشر:} - издатель. Если содержит {@code " - "} в конце
 *       (типичный shamela pattern «название - страна»), хвост идёт в
 *       publicationPlace</li>
 *   <li>{@code مكان النشر:} - явное место издания, имеет приоритет над
 *       heuristic split publisher'а</li>
 *   <li>{@code الطبعة:} - значение содержит arabic ordinal (الأولى /
 *       الثانية / ...) → editionNumber, + опционально год хиджры/григориан</li>
 *   <li>{@code عام النشر:} / {@code سنة النشر:} - год публикации (отдельный
 *       marker когда не помещён в الطبعة)</li>
 * </ul>
 *
 * <p>Год: arabic-indic digits {@code ٠-٩} перед маркером {@code هـ} (хиджра)
 * или {@code م} (григориан). Конвертация в обычные digits через
 * {@link Character#getNumericValue}.
 *
 * <p>Edition number: словарь {@code "الأولى"=1, "الثانية"=2, ...}. ASCII-числа
 * тоже принимаются (на случай arabic-indic-digits-only edition).
 */
@Component
public class ShamelaBibliographyParser {

    private static final String SHAMELA_SEPARATOR = "\\\\r";

    private static final Pattern MUHAQQIQ = compileField(
            "(?:المحقق|حققه ووضع حواشيه|تحقيق)"
    );
    private static final Pattern PUBLISHER = compileField("الناشر");
    private static final Pattern PUBLICATION_PLACE = compileField("مكان النشر");
    private static final Pattern EDITION = compileField("الطبعة");
    private static final Pattern YEAR_LINE = compileField("(?:عام النشر|سنة النشر)");

    private static final Pattern HIJRI_YEAR = Pattern.compile(
            "([\\d٠-٩]+)\\s*هـ"
    );
    private static final Pattern GREGORIAN_YEAR = Pattern.compile(
            "([\\d٠-٩]+)\\s*م(?:\\s|$|\\\\)"
    );

    private static final java.util.Map<String, Integer> ARABIC_ORDINALS = java.util.Map.ofEntries(
            java.util.Map.entry("الأولى", 1),
            java.util.Map.entry("الاولى", 1),
            java.util.Map.entry("الثانية", 2),
            java.util.Map.entry("الثالثة", 3),
            java.util.Map.entry("الرابعة", 4),
            java.util.Map.entry("الخامسة", 5),
            java.util.Map.entry("السادسة", 6),
            java.util.Map.entry("السابعة", 7),
            java.util.Map.entry("الثامنة", 8),
            java.util.Map.entry("التاسعة", 9),
            java.util.Map.entry("العاشرة", 10)
    );

    public ParsedBibliography parse(String bibliography) {
        if (bibliography == null || bibliography.isBlank()) {
            return ParsedBibliography.empty();
        }

        String muhaqqiq = extract(bibliography, MUHAQQIQ);
        String publisherRaw = extract(bibliography, PUBLISHER);
        String publicationPlace = extract(bibliography, PUBLICATION_PLACE);
        String editionRaw = extract(bibliography, EDITION);
        String yearLine = extract(bibliography, YEAR_LINE);

        String publisher = publisherRaw;
        if (publisher != null && publicationPlace == null) {
            int dashIdx = publisher.lastIndexOf(" - ");
            if (dashIdx > 0) {
                String candidate = publisher.substring(dashIdx + 3).trim();
                if (!candidate.isEmpty() && candidate.length() < publisher.length() / 2 + 1) {
                    publicationPlace = candidate;
                    publisher = publisher.substring(0, dashIdx).trim();
                }
            }
        }

        Integer editionNumber = parseEditionNumber(editionRaw);
        Integer hijri = parseYear(joinNullable(editionRaw, yearLine), HIJRI_YEAR);
        Integer gregorian = parseYear(joinNullable(editionRaw, yearLine), GREGORIAN_YEAR);

        return new ParsedBibliography(
                blank(muhaqqiq),
                blank(publisher),
                blank(publicationPlace),
                editionNumber,
                hijri,
                gregorian
        );
    }

    private static Pattern compileField(String markerAlternation) {
        return Pattern.compile(
                "(?:^|" + SHAMELA_SEPARATOR + ")" + markerAlternation
                        + "\\s*:\\s*(.+?)(?=" + SHAMELA_SEPARATOR + "|$)",
                Pattern.DOTALL
        );
    }

    private static String extract(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static Integer parseEditionNumber(String editionText) {
        if (editionText == null) return null;
        for (var entry : ARABIC_ORDINALS.entrySet()) {
            if (editionText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        Matcher digits = Pattern.compile("([\\d٠-٩]+)").matcher(editionText);
        if (digits.find()) {
            String raw = arabicIndicToAscii(digits.group(1));
            try {
                int n = Integer.parseInt(raw);
                if (n >= 1 && n <= 99) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Integer parseYear(String text, Pattern yearPattern) {
        if (text == null) return null;
        Matcher m = yearPattern.matcher(text);
        if (m.find()) {
            String digits = arabicIndicToAscii(m.group(1));
            try {
                int year = Integer.parseInt(digits);
                if (year >= 1 && year <= 9999) {
                    return year;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
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

    private static String joinNullable(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " " + b;
    }

    private static String blank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
