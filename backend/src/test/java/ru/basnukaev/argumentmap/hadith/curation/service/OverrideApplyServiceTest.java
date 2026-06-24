package ru.basnukaev.argumentmap.hadith.curation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.curation.domain.FieldOverride;
import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;

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

    private static FieldOverride ov(OverrideEntity entity, UUID id, String field,
                                    String value, boolean hidden) {
        // позиционно: ..., overrideValue, isNullOverride, hidden, ...
        return new FieldOverride(UUID.randomUUID(), entity.tableName(),
                id, field, value, false, hidden, UUID.randomUUID(), Instant.now(), null);
    }

    @Test
    void applyRuling_editsMetaAndHidesRulerName_keepsHadithIdAndMetadata() {
        UUID id = UUID.randomUUID();
        UUID hadithId = UUID.randomUUID();
        HadithRuling base = new HadithRuling(id, hadithId, "البخاري", 256, "حسن",
                "كتاب", 1, 2, "{\"k\":1}", Instant.now());
        OverrideSet set = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_RULINGS, id, "ruling_text", "صحيح", false),
                ov(OverrideEntity.HD_RULINGS, id, "ruler_name", null, true)));   // field-hide

        HadithRuling result = OverrideApplyService.apply(base, set);

        assertThat(result.rulingText()).isEqualTo("صحيح");
        assertThat(result.rulerName()).isNull();                 // field-hide → null
        // позиционная корректность: FK и метаданные не сдвинуты
        assertThat(result.hadithId()).isEqualTo(hadithId);
        assertThat(result.rulerDeathYear()).isEqualTo(256);
        assertThat(result.bookName()).isEqualTo("كتاب");
        assertThat(result.metadata()).isEqualTo("{\"k\":1}");
    }

    @Test
    void applyMatn_editsTranslationAndMeta_firstSourceArabicUntouched() {
        UUID id = UUID.randomUUID();
        Matn base = new Matn(id, UUID.randomUUID(), "نص-عربي", "نص-منوّن", "ru-old", "en-old",
                null, 5, 10, 1, true, "сводка", "{}", Instant.now());
        OverrideSet set = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_MATNS, id, "text_ru", "ru-new", false),
                ov(OverrideEntity.HD_MATNS, id, "page_no", "42", false)));

        Matn result = OverrideApplyService.apply(base, set);

        assertThat(result.textRu()).isEqualTo("ru-new");
        assertThat(result.pageNo()).isEqualTo(42);
        // первоисточник нетронут (apply его даже не читает)
        assertThat(result.textAr()).isEqualTo("نص-عربي");
        assertThat(result.textArNormalized()).isEqualTo("نص-منوّن");
        // неперекрытые поля + флаг сохранены
        assertThat(result.textEn()).isEqualTo("en-old");
        assertThat(result.isPrimary()).isTrue();
    }

    @Test
    void applyWithPrimaryTranslation_primaryMatn_overlaysHadithKeyedTranslation() {
        // Фаза 6 (§10 вопрос 2): перевод primary-матна ключуется hadith_id
        // (СИНТЕТИЧЕСКИЕ primary_text_ru/en), наложение на apply primary-матна.
        UUID hadithId = UUID.randomUUID();
        UUID matnId = UUID.randomUUID();
        Matn primary = new Matn(matnId, hadithId, "نص", "نص", null, null,
                null, 1, null, null, true, null, "{}", Instant.now());
        OverrideSet primaryTr = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_MATNS, hadithId, "primary_text_ru", "ru-перевод", false),
                ov(OverrideEntity.HD_MATNS, hadithId, "primary_text_en", "en-translation", false)));

        Matn result = OverrideApplyService.applyWithPrimaryTranslation(
                primary, OverrideSet.EMPTY, primaryTr, hadithId);

        assertThat(result.textRu()).isEqualTo("ru-перевод");
        assertThat(result.textEn()).isEqualTo("en-translation");
        // первоисточник и флаг нетронуты
        assertThat(result.textAr()).isEqualTo("نص");
        assertThat(result.isPrimary()).isTrue();
    }

    @Test
    void applyWithPrimaryTranslation_nonPrimaryMatn_ignoresHadithKeyedTranslation() {
        // не-primary матн: hadith-keyed primary_text_* к нему НЕ применяется
        UUID hadithId = UUID.randomUUID();
        UUID matnId = UUID.randomUUID();
        Matn variant = new Matn(matnId, hadithId, "نص-вариация", "نص", "ru-base", null,
                null, 2, null, null, false, null, "{}", Instant.now());
        OverrideSet primaryTr = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_MATNS, hadithId, "primary_text_ru", "не-применить", false)));

        Matn result = OverrideApplyService.applyWithPrimaryTranslation(
                variant, OverrideSet.EMPTY, primaryTr, hadithId);

        // per-matn значение сохранено, primary-перевод не просочился
        assertThat(result.textRu()).isEqualTo("ru-base");
    }

    @Test
    void applyWithPrimaryTranslation_primaryOverridesPerMatnTranslation() {
        // приоритет: human-правка primary-перевода (hadith-keyed) поверх
        // per-matn text_ru (matn-keyed) на том же primary-матне
        UUID hadithId = UUID.randomUUID();
        UUID matnId = UUID.randomUUID();
        Matn primary = new Matn(matnId, hadithId, "نص", "نص", "ru-стар", null,
                null, 1, null, null, true, null, "{}", Instant.now());
        OverrideSet perMatn = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_MATNS, matnId, "text_ru", "ru-per-matn", false)));
        OverrideSet primaryTr = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_MATNS, hadithId, "primary_text_ru", "ru-primary", false)));

        Matn result = OverrideApplyService.applyWithPrimaryTranslation(
                primary, perMatn, primaryTr, hadithId);

        assertThat(result.textRu()).isEqualTo("ru-primary");
    }

    @Test
    void applySanad_overridesChainGradeAndPrimaryChain() {
        UUID id = UUID.randomUUID();
        Sanad base = new Sanad(id, UUID.randomUUID(), "ضعيف", null, null, false, "{}", Instant.now());
        OverrideSet set = OverrideSet.group(List.of(
                ov(OverrideEntity.HD_SANADS, id, "chain_grade", "صحيح", false),
                ov(OverrideEntity.HD_SANADS, id, "primary_chain", "true", false)));

        Sanad result = OverrideApplyService.apply(base, set);

        assertThat(result.chainGrade()).isEqualTo("صحيح");
        assertThat(result.primaryChain()).isTrue();
    }
}
