package ru.basnukaev.argumentmap.hadith.curation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;

/** Unit-тесты чистого наложения overrides на доменные records (без БД). */
class OverrideApplyServiceTest {

    private static FieldOverride hadithOv(UUID id, String field, String value) {
        return new FieldOverride(UUID.randomUUID(), OverrideEntity.HD_HADITHS.tableName(),
                id, field, value, false, false, UUID.randomUUID(), Instant.now(), null);
    }

    @Test
    void applyHadith_overridesAuthenticity_butFirstSourceUntouched() {
        UUID id = UUID.randomUUID();
        Hadith base = new Hadith(id, null, 1, "نص-منوّن", "VARIANT", null, "{}", Instant.now(),
                "alminasa", "1-1", "مرفوع", "باب", "تحت-باب", "<a>full-text</a>", null);
        OverrideSet ov = OverrideSet.group(List.of(hadithOv(id, "authenticity", "SAHIH")));

        Hadith result = OverrideApplyService.apply(base, ov);

        assertThat(result.authenticity()).isEqualTo("SAHIH");
        // первоисточник нетронут — apply его даже не читает из набора
        assertThat(result.normalizedMatn()).isEqualTo("نص-منوّن");
        assertThat(result.fullTextAr()).isEqualTo("<a>full-text</a>");
        // неперекрытые поля сохранены
        assertThat(result.status()).isEqualTo("VARIANT");
        assertThat(result.chapterAr()).isEqualTo("باب");
    }

    @Test
    void applyHadith_noOverrideForId_returnsSameInstance() {
        UUID id = UUID.randomUUID();
        Hadith base = new Hadith(id, null, 1, "n", "CANONICAL", null, "{}", Instant.now());
        assertThat(OverrideApplyService.apply(base, OverrideSet.EMPTY)).isSameAs(base);
    }

    @Test
    void applyNarrator_overridesReliabilityAndTabaqa() {
        UUID id = UUID.randomUUID();
        Narrator base = new Narrator(id, null, "علقمة", "علقمه", null, null, null, null,
                null, null, null, "UNKNOWN", null, 0, "{}", Instant.now());
        OverrideSet ov = OverrideSet.group(List.of(
                new FieldOverride(UUID.randomUUID(), OverrideEntity.HD_NARRATORS.tableName(),
                        id, "reliability_grade", "THIQA", false, false, UUID.randomUUID(), Instant.now(), null),
                new FieldOverride(UUID.randomUUID(), OverrideEntity.HD_NARRATORS.tableName(),
                        id, "tabaqa", "الثانية", false, false, UUID.randomUUID(), Instant.now(), null),
                new FieldOverride(UUID.randomUUID(), OverrideEntity.HD_NARRATORS.tableName(),
                        id, "name_ar", "علقمة بن وقاص", false, false, UUID.randomUUID(), Instant.now(), null)));

        Narrator result = OverrideApplyService.apply(base, ov);

        assertThat(result.reliabilityGrade()).isEqualTo("THIQA");
        assertThat(result.tabaqa()).isEqualTo("الثانية");
        // name_ar переопределяется (фиксирует позиционную корректность record-конструктора)
        assertThat(result.nameAr()).isEqualTo("علقمة بن وقاص");
        // name_ar_normalized — производная, не трогается на apply
        assertThat(result.nameArNormalized()).isEqualTo("علقمه");
    }
}
