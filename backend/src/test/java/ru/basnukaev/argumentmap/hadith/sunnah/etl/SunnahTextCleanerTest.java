package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit-тест чистки текста дампа sunnah.com (без Spring/БД). Реальный matn
 * содержит inline-разметку (HTML, quran-якоря, footnote-маркеры), которую
 * нужно срезать перед записью в hd_matns/normalized_matn.
 */
class SunnahTextCleanerTest {

    @Test
    void strips_html_tags_keeping_inner_text() {
        assertThat(SunnahTextCleaner.clean("<p>Narrated X:<br>the text</p>"))
                .isEqualTo("Narrated X: the text");
    }

    @Test
    void strips_quran_anchor_keeping_verse_text() {
        // <A href="javascript:openquran(...)">аят</A> — убираем тег, аят оставляем
        assertThat(SunnahTextCleaner.clean(
                "قال <A id=\"q1\" name=\"q1\" href=\"javascript:openquran(5,82,82)\">الذين امنوا</A> ثم"))
                .isEqualTo("قال الذين امنوا ثم");
    }

    @Test
    void strips_footnote_markers() {
        // <c_qNN>…</c_qNN> и мисматч-разметка снимаются целиком
        assertThat(SunnahTextCleaner.clean("نص <c_q24>متن</c_q24> بعد"))
                .isEqualTo("نص متن بعد");
        assertThat(SunnahTextCleaner.clean("الف <a/l/> باء")).isEqualTo("الف باء");
    }

    @Test
    void decodes_html_entities() {
        assertThat(SunnahTextCleaner.clean("قال &quot;نعم&quot; &amp; لا"))
                .isEqualTo("قال \"نعم\" & لا");
    }

    @Test
    void collapses_whitespace() {
        assertThat(SunnahTextCleaner.clean("  a\n\n  b\t c  ")).isEqualTo("a b c");
    }

    @Test
    void inserts_word_boundary_for_adjacent_tags() {
        // тег между словами без пробела не должен склеить слова
        assertThat(SunnahTextCleaner.clean("قال<A href=\"x\">الذين</A>ثم"))
                .isEqualTo("قال الذين ثم");
    }

    @Test
    void null_stays_null_blank_becomes_empty() {
        assertThat(SunnahTextCleaner.clean(null)).isNull();
        assertThat(SunnahTextCleaner.clean("   ")).isEmpty();
        assertThat(SunnahTextCleaner.clean("<p></p>")).isEmpty();
    }
}
