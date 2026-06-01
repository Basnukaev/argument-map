package ru.basnukaev.argumentmap.hadith.service;

/**
 * Нормализация арабского текста для search/dedup matn'ов (Phase 5 ETL).
 *
 * <p>ETL импортирует тысячи matn'ов — нормализованная форма вычисляется,
 * а не вбивается руками (как было в DevHadithSeeder). Правила (стандартная
 * арабская нормализация, как в Lucene ArabicNormalizer):
 * <ol>
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
 * <p>Нормализация лоссиная (по дизайну — для нечувствительного к огласовкам
 * поиска). Оригинальный текст хранится в {@code hd_matns.text_ar}.
 */
public final class ArabicTextNormalizer {

    private ArabicTextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // огласовки и комбинируемые знаки (U+064B–U+065F) + надстрочный
            // алиф (U+0670) + татвиль-кашида (U+0640) — удаляем
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
