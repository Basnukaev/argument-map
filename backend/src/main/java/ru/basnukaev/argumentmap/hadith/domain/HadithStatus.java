package ru.basnukaev.argumentmap.hadith.domain;

import java.util.Set;

/**
 * Whitelist статуса хадиса (mirror CHECK constraint в hd_hadiths).
 * Vision 49d Section 2.6 Phase 1.
 *
 * <ul>
 *   <li>CANONICAL - в основных сборниках (Бухари/Муслим/etc)</li>
 *   <li>VARIANT - вариант с другим matn или sanad</li>
 *   <li>WEAK - daif</li>
 *   <li>FABRICATED - mawdu</li>
 * </ul>
 */
public final class HadithStatus {

    public static final String CANONICAL = "CANONICAL";
    public static final String VARIANT = "VARIANT";
    public static final String WEAK = "WEAK";
    public static final String FABRICATED = "FABRICATED";

    public static final Set<String> ALL = Set.of(CANONICAL, VARIANT, WEAK, FABRICATED);

    private HadithStatus() {
    }

    public static boolean isValid(String status) {
        return status != null && ALL.contains(status);
    }
}
