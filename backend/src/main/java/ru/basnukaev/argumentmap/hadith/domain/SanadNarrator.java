package ru.basnukaev.argumentmap.hadith.domain;

import java.util.UUID;

/**
 * Position-ordered линковка sanad ↔ narrator. Vision 49d Section
 * 2.6 Phase 1.
 *
 * <p>position 0 = ближайший к Пророку ﷺ (или сам Пророк ﷺ для
 * ahadith-i qudsi). transmission_phrase - «حدثنا» / «أخبرنا» / «عن»
 * - семантика передачи влияет на надёжность.
 */
public record SanadNarrator(
        UUID sanadId,
        int position,
        UUID narratorId,
        String transmissionPhrase
) {
}
