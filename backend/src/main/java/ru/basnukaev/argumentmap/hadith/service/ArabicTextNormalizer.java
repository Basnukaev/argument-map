package ru.basnukaev.argumentmap.hadith.service;

import java.text.Normalizer;

/**
 * Нормализация арабского текста для search/dedup matn'ов (Phase 5 ETL).
 *
 * <p>ETL импортирует тысячи matn'ов — нормализованная форма вычисляется,
 * а не вбивается руками (как было в DevHadithSeeder). Конвейер:
 * <ol>
 *   <li><b>NFKC</b>-предобработка: раскрывает presentation forms
 *       (FB50+/FE70+), лигатуру лям-алиф (U+FEFB → لا) и
 *       NFD-декомпозированные носители хамзы в канонические буквы +
 *       комбинируемые знаки;</li>
 *   <li>удаление огласовок/танвина/шадды/сукуна (U+064B–U+065F) и
 *       надстрочного алифа (U+0670);</li>
 *   <li>удаление татвиля-кашиды (U+0640);</li>
 *   <li>сведение алифов أإآٱ (U+0622/0623/0625/0671) → ا (U+0627);</li>
 *   <li>алиф-максура ى (U+0649) → ي (U+064A);</li>
 *   <li>та-марбута ة (U+0629) → ه (U+0647);</li>
 *   <li>хамза-носители ؤ (U+0624) → و, ئ (U+0626) → ي; одиночная хамза
 *       ء (U+0621) удаляется;</li>
 *   <li>схлопывание пробельных последовательностей в один пробел + trim.</li>
 * </ol>
 *
 * <p>Аналогично Lucene ArabicNormalizer, но <b>агрессивнее</b>: шире
 * диапазон удаляемой диакритики, плюс сведение хамза-носителей, удаление
 * одиночной хамзы и NFKC-предобработка. Нормализация лоссиная (по дизайну —
 * для нечувствительного к огласовкам поиска); оригинал в {@code hd_matns.text_ar}.
 * Идемпотентна: {@code normalize(normalize(x)) == normalize(x)}.
 */
public final class ArabicTextNormalizer {

    private ArabicTextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        // NFKC раскрывает presentation forms / лигатуры / NFD-носители хамзы
        // в канонические буквы + комбинируемые знаки, которые ниже снимаются
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(nfkc.length());
        for (int i = 0; i < nfkc.length(); i++) {
            char c = nfkc.charAt(i);
            // огласовки и комбинируемые знаки (U+064B–U+065F, включая
            // комбинируемую хамзу U+0654/0655 после NFD-декомпозиции) +
            // надстрочный алиф (U+0670) + татвиль-кашида (U+0640) — удаляем
            if ((c >= 'ً' && c <= 'ٟ') || c == 'ٰ' || c == 'ـ') {
                continue;
            }
            switch (c) {
                case 'آ', 'أ', 'إ', 'ٱ' -> sb.append('ا'); // آأإٱ → ا
                case 'ى' -> sb.append('ي'); // ى → ي
                case 'ة' -> sb.append('ه'); // ة → ه
                case 'ؤ' -> sb.append('و'); // ؤ → و
                case 'ئ' -> sb.append('ي'); // ئ → ي
                case 'ء' -> { /* ء — удаляем */ }
                default -> sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}
