package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ShamelaTextCleanerTest {

    @Test
    void clean_null_returnsNull() {
        assertNull(ShamelaTextCleaner.clean(null));
    }

    @Test
    void clean_emptyString_returnsAsIs() {
        assertEquals("", ShamelaTextCleaner.clean(""));
    }

    @Test
    void clean_pureArabic_unchanged() {
        String arabic = "بسم الله الرحمن الرحيم";
        assertEquals(arabic, ShamelaTextCleaner.clean(arabic));
    }

    @Test
    void clean_shamelaIconPlaceholder_removed() {
        // 舄 = U+8204, наблюдаемый icon-font placeholder shamela перед
        // <span data-type='title'> для якоря-закладки
        String input = "舄<span data-type='title'>مقدمة المحقق</span>";
        String expected = "<span data-type='title'>مقدمة المحقق</span>";
        assertEquals(expected, ShamelaTextCleaner.clean(input));
    }

    @Test
    void clean_multipleIconPlaceholders_allRemoved() {
        String input = "舄первый舄второй舄третий";
        assertEquals("первыйвторойтретий", ShamelaTextCleaner.clean(input));
    }

    @Test
    void clean_otherCjkBlocks_alsoRemoved() {
        // hiragana, katakana, hangul - все потенциальные icon-font placeholders
        String input = "арабскийあяпонскийアтоже가корейский";
        assertEquals("арабскийяпонскийтожекорейский", ShamelaTextCleaner.clean(input));
    }

    @Test
    void clean_arabicPresentationForms_preserved() {
        // U+FDC0 ﵀ = "رحمه الله", U+FDFA ﷺ = салават, U+FDC9 ﵉ = "عليه السلام"
        // Это смысловая часть текста, regex их не должен трогать
        String input = "ابن كثير ﷀ قال النبي ﷺ";
        assertEquals(input, ShamelaTextCleaner.clean(input));
    }

    @Test
    void clean_latinAndCyrillic_preserved() {
        String input = "Mixed text русский 123";
        assertEquals(input, ShamelaTextCleaner.clean(input));
    }

    @Test
    void clean_realShamelaSample_cleansIconLeavesContent() {
        // Реальный sample из БД (предисловие المقدمة)
        String input = "<p>舄بسم الله الرحمن الرحيم</p><p>الحمد لله ربّ العالمين</p>";
        String expected = "<p>بسم الله الرحمن الرحيم</p><p>الحمد لله ربّ العالمين</p>";
        assertEquals(expected, ShamelaTextCleaner.clean(input));
    }
}
