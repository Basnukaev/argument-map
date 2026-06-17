package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaHadithMapper.RulingCandidate;
import ru.basnukaev.argumentmap.hadith.domain.HadithAuthenticity;

/**
 * Изолированный unit-тест keyword-эвристики достоверности
 * ({@link AlminasaHadithMapper#deriveAuthenticity}, спека 2026-06-17
 * §C8/C19/C21/D1): нормализованный подстрочный матч вердиктов →
 * модальный бакет; ничья → худший; нет совпадений → null.
 */
class AlminasaAuthenticityHeuristicTest {

    private static RulingCandidate ruling(String text) {
        return new RulingCandidate("البخاري", 256, text, "صحيح البخاري", 6, 1,
                "{\"source\":\"embedded\"}");
    }

    @Test
    void нет_рулингов_возвращает_null() {
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of())).isNull();
    }

    @Test
    void вердикт_без_ключевых_слов_возвращает_null() {
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("أخرجه في كتابه"))))
                .isNull();
    }

    @Test
    void сахих_по_подстроке_в_словоформе() {
        // «أورده في صحيحه» содержит صحيح как подстроку (صحيحه) → SAHIH
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("أورده في صحيحه"))))
                .isEqualTo(HadithAuthenticity.SAHIH);
    }

    @Test
    void сахих_аль_иснад_классифицируется_как_сахих() {
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("صحيح الإسناد"))))
                .isEqualTo(HadithAuthenticity.SAHIH);
    }

    @Test
    void хасан_классифицируется_как_хасан() {
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("حديث حسن"))))
                .isEqualTo(HadithAuthenticity.HASAN);
    }

    @Test
    void даиф_с_огласовками_и_усилением_классифицируется_как_даиф() {
        // огласовки снимаются нормализацией; «ضعيف جدا» содержит ضعيف
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("ضَعِيف جدا"))))
                .isEqualTo(HadithAuthenticity.DAIF);
    }

    @Test
    void мауду_классифицируется_как_мауду() {
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("موضوع"))))
                .isEqualTo(HadithAuthenticity.MAUDU);
    }

    @Test
    void хасан_сахих_в_одном_вердикте_берёт_худший_хасан() {
        // составной грейд «حسن صحيح» содержит оба слова → худший по приоритету = HASAN
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("حسن صحيح"))))
                .isEqualTo(HadithAuthenticity.HASAN);
    }

    @Test
    void модальный_вердикт_среди_нескольких_рулингов() {
        // два SAHIH против одного DAIF → мода SAHIH
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("صحيح"), ruling("صحيح الإسناد"), ruling("ضعيف"))))
                .isEqualTo(HadithAuthenticity.SAHIH);
    }

    @Test
    void равная_частота_берёт_худший() {
        // один SAHIH и один DAIF — ничья → худший DAIF
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("صحيح"), ruling("ضعيف"))))
                .isEqualTo(HadithAuthenticity.DAIF);
    }

    @Test
    void мауду_бьёт_даиф_при_равной_частоте() {
        assertThat(AlminasaHadithMapper.deriveAuthenticity(List.of(
                ruling("ضعيف"), ruling("موضوع"))))
                .isEqualTo(HadithAuthenticity.MAUDU);
    }
}
