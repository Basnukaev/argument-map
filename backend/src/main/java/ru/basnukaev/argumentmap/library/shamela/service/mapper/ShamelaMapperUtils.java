package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилиты для shamela-маппинга в доменную модель. Не {@code @Service} -
 * чистые функции, используются через static-импорт.
 */
final class ShamelaMapperUtils {

    static final String EMPTY_TITLE_PLACEHOLDER = "(без названия)";

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private ShamelaMapperUtils() {
    }

    /**
     * Парсит {@code shamela_title.page_ref} в номер начальной страницы
     * главы. Может быть {@code "1"}, {@code "1-3"} (range), пустым или
     * содержать арабские цифры. Берём первое целое число (ASCII digits) -
     * shamela page_ref в latin-цифрах, не в арабских восточных. При
     * неудаче парсинга или non-positive значении - null.
     */
    static Integer parseStartPage(String pageRef) {
        if (pageRef == null || pageRef.isBlank()) {
            return null;
        }
        Matcher m = DIGITS.matcher(pageRef);
        if (m.find()) {
            int value = Integer.parseInt(m.group());
            return value > 0 ? value : null;
        }
        return null;
    }

    static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String sanitizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY_TITLE_PLACEHOLDER;
        }
        return raw.trim();
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
