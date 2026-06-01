package ru.basnukaev.argumentmap.hadith.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit-тест нормализатора арабского текста (без Spring/БД). Phase 5 ETL
 * шаг 2.c: ETL импортирует тысячи matn'ов, нормализация для search/dedup
 * вычисляется, а не вбивается руками (как в DevHadithSeeder).
 */
class ArabicTextNormalizerTest {

    @Test
    void removes_harakat_and_shadda() {
        // إِنَّمَا (с кясрой/шаддой/фатхой) → انما
        assertThat(ArabicTextNormalizer.normalize("إِنَّمَا")).isEqualTo("انما");
    }

    @Test
    void folds_alif_variants_to_bare_alif() {
        assertThat(ArabicTextNormalizer.normalize("أحمد إبراهيم آمن ٱلله"))
                .isEqualTo("احمد ابراهيم امن الله");
    }

    @Test
    void folds_alif_maksura_to_ya() {
        assertThat(ArabicTextNormalizer.normalize("موسى")).isEqualTo("موسي");
    }

    @Test
    void folds_taa_marbuta_to_ha() {
        assertThat(ArabicTextNormalizer.normalize("صلاة")).isEqualTo("صلاه");
    }

    @Test
    void removes_tatweel() {
        assertThat(ArabicTextNormalizer.normalize("محـــمـد")).isEqualTo("محمد");
    }

    @Test
    void normalizes_hamza_carriers() {
        assertThat(ArabicTextNormalizer.normalize("مؤمن")).isEqualTo("مومن");
        assertThat(ArabicTextNormalizer.normalize("قائل")).isEqualTo("قايل");
        // standalone hamza removed
        assertThat(ArabicTextNormalizer.normalize("شيء")).isEqualTo("شي");
    }

    @Test
    void collapses_and_trims_whitespace() {
        assertThat(ArabicTextNormalizer.normalize("  ابن   عمر \t عن  "))
                .isEqualTo("ابن عمر عن");
    }

    @Test
    void null_and_blank_become_empty() {
        assertThat(ArabicTextNormalizer.normalize(null)).isEmpty();
        assertThat(ArabicTextNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    void full_matn_is_normalized_consistently() {
        String diacritized = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ";
        String plain = "إنما الأعمال بالنيات";
        // одинаковый результат вне зависимости от огласовок
        assertThat(ArabicTextNormalizer.normalize(diacritized))
                .isEqualTo(ArabicTextNormalizer.normalize(plain))
                .isEqualTo("انما الاعمال بالنيات");
    }

    @Test
    void unfolds_presentation_forms_and_lam_alef_ligature_via_nfkc() {
        // лигатура лям-алиф ﻻ (U+FEFB) → لا
        assertThat(ArabicTextNormalizer.normalize("ﻻ")).isEqualTo("لا");
        // isolated-форма алиф-с-маддой ﺁ (U+FE81) → ا
        assertThat(ArabicTextNormalizer.normalize("ﺁ")).isEqualTo("ا");
    }

    @Test
    void equivalent_for_decomposed_nfd_input() {
        // одинаковый результат для NFC- и NFD-представления одного текста
        String composed = "أئؤ";
        String decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD);
        assertThat(ArabicTextNormalizer.normalize(decomposed))
                .isEqualTo(ArabicTextNormalizer.normalize(composed))
                .isEqualTo("ايو");
    }

    @Test
    void passes_through_latin_and_digits_as_text() {
        // нормализатор — для текста: латиница и цифры (вкл. арабо-индийские) сохраняются
        assertThat(ArabicTextNormalizer.normalize("رقم ٤٥ ref-12"))
                .isEqualTo("رقم ٤٥ ref-12");
    }

    @Test
    void strips_dagger_alif_and_tanwin_at_range_boundaries() {
        // надстрочный алиф U+0670
        assertThat(ArabicTextNormalizer.normalize("هَٰذَا")).isEqualTo("هذا");
        // танвин-фатха U+064B (нижняя граница диапазона диакритики)
        assertThat(ArabicTextNormalizer.normalize("محمدًا")).isEqualTo("محمدا");
    }

    @Test
    void is_idempotent() {
        for (String s : new String[]{
                "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "موسى", "صلاة", "ﻻ",
                "محـــمـد", "رقم ٤٥ ref-12", "مؤمن قائل شيء"}) {
            String once = ArabicTextNormalizer.normalize(s);
            assertThat(ArabicTextNormalizer.normalize(once)).isEqualTo(once);
        }
    }
}
