package ru.basnukaev.argumentmap.hadith.domain;

import java.util.Set;

/**
 * Whitelist надёжности narrator (mirror CHECK constraint в hd_narrators).
 * Vision 49d Section 2.6 Phase 1.
 *
 * <ul>
 *   <li>THIQA - надёжный (highest)</li>
 *   <li>SADUQ - правдивый</li>
 *   <li>MAQBUL - приемлемый</li>
 *   <li>DAIF - слабый</li>
 *   <li>MATRUK - оставленный</li>
 *   <li>SAHABI - сподвижник (по иджме 'адль, не оценивается через
 *       джарх ва тадиль; стоит вне шкалы критики)</li>
 *   <li>UNKNOWN - не определено</li>
 * </ul>
 */
public final class NarratorReliability {

    public static final String THIQA = "THIQA";
    public static final String SADUQ = "SADUQ";
    public static final String MAQBUL = "MAQBUL";
    public static final String DAIF = "DAIF";
    public static final String MATRUK = "MATRUK";
    public static final String SAHABI = "SAHABI";
    public static final String UNKNOWN = "UNKNOWN";

    public static final Set<String> ALL = Set.of(THIQA, SADUQ, MAQBUL, DAIF, MATRUK, SAHABI, UNKNOWN);

    private NarratorReliability() {
    }

    public static boolean isValid(String grade) {
        if (grade == null) return true;
        return ALL.contains(grade);
    }
}
