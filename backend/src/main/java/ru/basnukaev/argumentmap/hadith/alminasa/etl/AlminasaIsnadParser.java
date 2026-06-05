package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.IsnadLink;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.ParsedIsnad;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Детерминированный (БЕЗ AI) парсер иснада из {@code full_text_ar} alminasa.
 *
 * <p>Источник цепи — rawy-теги {@code <a class=rawy id=N>ИМЯ</a>} (атрибуты БЕЗ
 * кавычек). Семантика формул — «сегмент ПОСЛЕ тега» (реш. 2 плана): текст между
 * закрывающим тегом рави {@code c_i} и открывающим тегом {@code c_{i+1}} (для
 * последнего — до {@code <a class=matn>}, а если matn-тега нет — до конца строки)
 * — собственная речь {@code c_i} о том, как ОН получил хадис от следующего звена.
 * Сегмент ПЕРЕД первым тегом — формула составителя ({@code collectorPhrase}).
 *
 * <p>Формула извлекается по приоритетному списку токенов (равенство нормализованного
 * слова, не substring: {@code عنه ≠ عن}); найденный токен возвращается в
 * нормализованной форме. Ничего не найдено → {@code null}.
 */
public final class AlminasaIsnadParser {

    /** {@code <a class=rawy id=N>ИМЯ</a>} — атрибуты без кавычек, имя с пробелами/диакритикой. */
    private static final Pattern RAWY_TAG = Pattern.compile("<a class=rawy id=(\\d+)>(.*?)</a>");

    /** Маркер начала матна: всё после него к иснаду не относится. */
    private static final String MATN_MARKER = "<a class=matn>";

    /**
     * Приоритетный список формул передачи: нормализованный токен → каноническая
     * форма. Порядок = приоритет: первый найденный в сегменте токен побеждает.
     *
     * <p>Включены писцовые аббревиатуры (live-находка Сессии 58: в Мустадраке
     * сплошь {@code ثنا}): ثنا/ثني = حدثنا/حدثني, أنا/نا = أخبرنا/حدثنا,
     * أنبأ = أنبأنا — они КАНОНИЗИРУЮТСЯ к полным формам, чтобы стрелки графа
     * и фильтры видели единообразные формулы. Сравнение пословное (равенство
     * нормализованного слова), короткие токены не ловят подстроки чужих слов.
     */
    private static final Map<String, String> FORMULA_TOKENS = tokenMap(new String[][]{
            {"حدثنا", "حدثنا"}, {"حدثني", "حدثني"},
            {"أخبرنا", "أخبرنا"}, {"أخبرني", "أخبرني"}, {"أنبأنا", "أنبأنا"},
            {"ثنا", "حدثنا"}, {"ثني", "حدثني"},
            {"أنا", "أخبرنا"}, {"نا", "حدثنا"}, {"أنبأ", "أنبأنا"},
            {"سمعت", "سمعت"}, {"سمع", "سمع"}, {"عن", "عن"}, {"أن", "أن"},
    });

    /** Фолбэк-формулы (ищутся только если приоритетные не найдены). */
    private static final Map<String, String> FALLBACK_TOKENS = tokenMap(new String[][]{
            {"قال", "قال"}, {"يقول", "يقول"},
    });

    private AlminasaIsnadParser() {
    }

    /**
     * Разбирает иснад из {@code full_text_ar}.
     *
     * @param fullTextAr полный текст хадиса с rawy-тегами и matn-маркером
     * @return цепь в порядке collector→companion + формула составителя;
     *         {@link ParsedIsnad#empty()} если вход null/blank или нет rawy-тегов
     */
    public static ParsedIsnad parse(String fullTextAr) {
        if (fullTextAr == null || fullTextAr.isBlank()) {
            return ParsedIsnad.empty();
        }

        // Иснад заканчивается на matn-маркере (если он есть) — обрезаем хвост-матн.
        int matnStart = fullTextAr.indexOf(MATN_MARKER);
        String isnadText = matnStart >= 0 ? fullTextAr.substring(0, matnStart) : fullTextAr;

        Matcher m = RAWY_TAG.matcher(isnadText);
        List<int[]> spans = new ArrayList<>(); // {tagStart, tagEnd}
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            ids.add(m.group(1));
            names.add(m.group(2).trim());
        }
        if (spans.isEmpty()) {
            return ParsedIsnad.empty();
        }

        // Сегмент ПЕРЕД первым тегом — формула составителя.
        String collectorPhrase = extractFormula(isnadText.substring(0, spans.get(0)[0]));

        List<IsnadLink> links = new ArrayList<>(spans.size());
        for (int i = 0; i < spans.size(); i++) {
            int segStart = spans.get(i)[1];
            int segEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : isnadText.length();
            String receivedVia = extractFormula(isnadText.substring(segStart, segEnd));
            links.add(new IsnadLink(ids.get(i), names.get(i), receivedVia));
        }

        return new ParsedIsnad(links, collectorPhrase);
    }

    /**
     * Извлекает нормализованную формулу из сегмента: нормализует сегмент, бьёт по
     * пробелам, ищет первое слово, равное токену из приоритетного списка (потом
     * фолбэк). Сравнение — равенство (не substring). Нет совпадений → {@code null}.
     */
    private static String extractFormula(String segment) {
        String normalized = ArabicTextNormalizer.normalize(segment);
        if (normalized.isBlank()) {
            return null;
        }
        List<String> words = List.of(normalized.split(" "));
        String hit = firstMatch(words, FORMULA_TOKENS);
        if (hit != null) {
            return hit;
        }
        return firstMatch(words, FALLBACK_TOKENS);
    }

    /**
     * Первый токен из {@code tokens} (в порядке приоритета), равный какому-либо
     * слову сегмента. Возвращает КАНОНИЧЕСКУЮ форму токена либо {@code null}.
     */
    private static String firstMatch(List<String> words, Map<String, String> tokens) {
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            if (words.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Нормализует пары токен→каноническая форма, сохраняя порядок приоритета. */
    private static Map<String, String> tokenMap(String[][] pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String[] pair : pairs) {
            result.put(ArabicTextNormalizer.normalize(pair[0]),
                    ArabicTextNormalizer.normalize(pair[1]));
        }
        return Collections.unmodifiableMap(result);
    }
}
