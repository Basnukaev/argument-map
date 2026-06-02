package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Извлекает academic citation metadata из неструктурированного arabic-текста
 * shamela bibliography (поле {@code lib_shamela_book.bibliography}). Этап 20.c.
 *
 * <p>Shamela хранит bibliography как один TEXT с разделителем между полями.
 * Реальный production-формат - **CR character** ({@code chr(13)}), но в
 * исходных Java-фикстурах и в некоторых staging-снимках встречается
 * literal {@code \\r} (2 char: backslash + r) - не сконвертированный escape.
 * Парсер ловит оба варианта через regex alternation, чтобы работать и на
 * текущем production-БД, и на тестовых фикстурах с escape-нотацией.
 * Каждое поле имеет arabic marker и текст после двоеточия. Парсер
 * консервативен - возвращает {@code null} для каждого поля если marker
 * не найден или значение пустое.
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

    // Разделитель: либо CR character (production), либо literal "\r"
    // (2 char escape - встречается в фикстурах и некоторых dump'ах).
    private static final String SHAMELA_SEPARATOR = "(?:\\r|\\\\r)";

    private static final Pattern MUHAQQIQ = compileField(
            "(?:المحقق|حققه ووضع حواشيه|تحقيق)"
    );
    private static final Pattern PUBLISHER = compileField("الناشر");
    private static final Pattern PUBLICATION_PLACE = compileField("مكان النشر");
    private static final Pattern EDITION = compileField("الطبعة");
    private static final Pattern YEAR_LINE = compileField("(?:عام النشر|سنة النشر)");

    // Thesis (рисала) markers - для академических диссертаций (миграция 58):
    //   رسالة: ماجستير، جامعة الإمام ... - كلية ... → degree + institution
    //   إشراف: د. فلان                              → supervisor
    //   العام الجامعي: ١٤٣٧ - ١٤٣٨ هـ                → academic year (→ hijri)
    private static final Pattern THESIS_LINE = compileField("رسالة");
    private static final Pattern SUPERVISOR = compileField("(?:إشراف|اشراف|المشرف)");
    private static final Pattern ACADEMIC_YEAR_LINE = compileField("(?:العام الجامعي|العام الدراسي)");

    private static final Pattern HIJRI_YEAR = Pattern.compile(
            "([\\d٠-٩]+)\\s*هـ"
    );
    private static final Pattern GREGORIAN_YEAR = Pattern.compile(
            "([\\d٠-٩]+)\\s*م(?:\\s|$|\\\\)"
    );

    private static final Map<String, Integer> ARABIC_ORDINALS = Map.ofEntries(
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
                // Часть ПОСЛЕ последнего « - » - место издания (город/страна),
                // часть ДО - издатель (дар/издательство). Shamela pattern
                // «الناشر: دار ... - المدينة».
                String candidate = publisher.substring(dashIdx + 3).trim();
                // Раньше здесь стоял char-length-ratio guard
                // (candidate.length() < publisher.length()/2 + 1) - он
                // молча НЕ резал короткого издателя с длинным названием
                // страны («دار طيبة - المملكة العربية السعودية»: 24 ≥ 18),
                // оставляя место издания приклеенным к publisher. Place -
                // топоним из 1-5 слов даже когда длинный в символах; именно
                // word-count, а не char-length, отделяет место от издателя.
                int candidateWords = candidate.isEmpty()
                        ? 0
                        : candidate.split("\\s+").length;
                if (!candidate.isEmpty() && candidateWords <= 5) {
                    publicationPlace = candidate;
                    publisher = publisher.substring(0, dashIdx).trim();
                }
            }
        }

        // Thesis-поля. Строка رسالة содержит degree + institution через
        // разделитель «،» либо « - »: "ماجستير، جامعة الإمام ... - كلية ...".
        // Первый сегмент = degree (ماجستير/دكتوراه), остаток = institution.
        String thesisLine = extract(bibliography, THESIS_LINE);
        String thesisDegree = null;
        String thesisInstitution = null;
        if (thesisLine != null) {
            Separator sep = firstSeparator(thesisLine);
            if (sep != null) {
                thesisDegree = thesisLine.substring(0, sep.index()).trim();
                // sep.length() - чтобы для « - » (3 символа) срезать весь
                // разделитель, иначе у institution остаётся ведущий «-».
                thesisInstitution = thesisLine.substring(sep.index() + sep.length()).trim();
            } else {
                // Нет разделителя - вся строка как degree (минимальный случай)
                thesisDegree = thesisLine.trim();
            }
        }
        String thesisSupervisor = extract(bibliography, SUPERVISOR);

        Integer editionNumber = parseEditionNumber(editionRaw);
        // Год: для изданных книг - الطبعة/عام النشر; для диссертаций -
        // العام الجامعي. Все источники складываем в извлечение года.
        String academicYearLine = extract(bibliography, ACADEMIC_YEAR_LINE);
        String yearSources = joinNullable(joinNullable(editionRaw, yearLine), academicYearLine);
        Integer hijri = parseYear(yearSources, HIJRI_YEAR);
        Integer gregorian = parseYear(yearSources, GREGORIAN_YEAR);

        return new ParsedBibliography(
                blank(muhaqqiq),
                blank(publisher),
                blank(publicationPlace),
                editionNumber,
                hijri,
                gregorian,
                blank(thesisDegree),
                blank(thesisSupervisor),
                blank(thesisInstitution)
        );
    }

    /** Позиция + длина разделителя degree/institution (length важна чтобы
     *  срезать весь « - », а не оставлять ведущий «-» у institution). */
    private record Separator(int index, int length) {
    }

    /**
     * Первый разделитель degree/institution в строке رسالة: arabic-запятая
     * «،», обычная запятая «,» (length 1) либо « - » (length 3). Возвращает
     * {@code null} если разделителя нет.
     */
    private static Separator firstSeparator(String s) {
        Separator best = null;
        for (String sep : new String[] {"،", ",", " - "}) {
            int idx = s.indexOf(sep);
            if (idx >= 0 && (best == null || idx < best.index())) {
                best = new Separator(idx, sep.length());
            }
        }
        return best;
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
