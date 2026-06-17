package ru.basnukaev.argumentmap.hadith.domain;

import java.util.Set;

/**
 * Whitelist достоверности хадиса — ось ДОСТОВЕРНОСТИ, ортогональная
 * {@link HadithStatus} (провенанс CANONICAL/VARIANT). Mirror CHECK
 * constraint в hd_hadiths.authenticity. Спека 2026-06-17 §C8/C19/C21/D1.
 *
 * <ul>
 *   <li>SAHIH — صحيح (достоверный)</li>
 *   <li>HASAN — حسن (хороший)</li>
 *   <li>DAIF — ضعيف (слабый)</li>
 *   <li>MAUDU — موضوع (выдуманный)</li>
 * </ul>
 *
 * <p>Значение выводится {@code AlminasaHadithMapper} keyword-эвристикой по
 * арабским вердиктам рулингов и потому приближённо (см. javadoc маппера);
 * NULL — вердиктов нет либо совпадений не найдено.
 */
public final class HadithAuthenticity {

    public static final String SAHIH = "SAHIH";
    public static final String HASAN = "HASAN";
    public static final String DAIF = "DAIF";
    public static final String MAUDU = "MAUDU";

    public static final Set<String> ALL = Set.of(SAHIH, HASAN, DAIF, MAUDU);

    private HadithAuthenticity() {
    }

    public static boolean isValid(String authenticity) {
        return authenticity != null && ALL.contains(authenticity);
    }
}
