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
}
