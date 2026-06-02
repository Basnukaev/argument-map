package ru.basnukaev.argumentmap.library.archiveorg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.library.archiveorg.ArchiveOrgDescriptionParser.ParsedDescription;

/**
 * Unit-тесты {@link ArchiveOrgDescriptionParser} (ADR-056): извлечение
 * gap-полей из арабского HTML-{@code description}. Без Spring/сети.
 */
class ArchiveOrgDescriptionParserTest {

    private final ArchiveOrgDescriptionParser parser = new ArchiveOrgDescriptionParser();

    /** Реальный fmhji description (из live-смока + фикстуры). */
    private static final String FMHJI_DESC =
            "<div><div align=\"right\">عنوان الكتاب: الفقه المنهجي على مذهب الإمام الشافعي</div>"
            + "المؤلف: مصطفى الخن ، مصطفى البغا ، علي الشربجي<br />\n"
            + "الناشر: دار القلم دمشق<br />\n"
            + "سنة النشر: 1433 - 2012<br />\n"
            + "عدد المجلدات : 3 <br />\n"
            + "رقم الطبعة :  الطبعة الثالثة عشر</div><div><br /></div>";

    @Test
    void fmhji_parsesAllPresentFields() {
        ParsedDescription p = parser.parse(FMHJI_DESC);

        assertThat(p.author()).isEqualTo("مصطفى الخن ، مصطفى البغا ، علي الشربجي");
        assertThat(p.publisher()).isEqualTo("دار القلم دمشق");
        assertThat(p.place()).isNull(); // м.метки нет → не сплитим город
        assertThat(p.yearHijri()).isEqualTo(1433);
        assertThat(p.yearGregorian()).isEqualTo(2012);
        assertThat(p.volumes()).isEqualTo(3);
        assertThat(p.editionNumber()).isEqualTo(13); // الثالثة عشر = 3 + 10
    }

    @Test
    void nullOrBlank_returnsEmpty() {
        assertThat(parser.parse(null).publisher()).isNull();
        assertThat(parser.parse("").volumes()).isNull();
        assertThat(parser.parse("   ").author()).isNull();
    }

    @Test
    void garbledNoLabels_returnsAllNull() {
        ParsedDescription p = parser.parse("<p>نص حر بلا حقول</p>");

        assertThat(p.author()).isNull();
        assertThat(p.publisher()).isNull();
        assertThat(p.place()).isNull();
        assertThat(p.editionNumber()).isNull();
        assertThat(p.yearHijri()).isNull();
        assertThat(p.yearGregorian()).isNull();
        assertThat(p.volumes()).isNull();
    }

    @Test
    void altLabels_taleefAndDarAlnashr() {
        ParsedDescription p = parser.parse(
                "تأليف: ابن تيمية<br />دار النشر: دار طيبة<br />مكان النشر: الرياض");

        assertThat(p.author()).isEqualTo("ابن تيمية");
        assertThat(p.publisher()).isEqualTo("دار طيبة");
        assertThat(p.place()).isEqualTo("الرياض"); // явная метка مكان النشر
    }

    @Test
    void onlyHijriYear() {
        ParsedDescription p = parser.parse("سنة النشر: 1420 هـ");

        assertThat(p.yearHijri()).isEqualTo(1420);
        assertThat(p.yearGregorian()).isNull();
    }

    @Test
    void onlyGregorianYear() {
        ParsedDescription p = parser.parse("عام النشر: 2005");

        assertThat(p.yearGregorian()).isEqualTo(2005);
        assertThat(p.yearHijri()).isNull();
    }

    @Test
    void editionAsDigit() {
        ParsedDescription p = parser.parse("الطبعة: 2");
        assertThat(p.editionNumber()).isEqualTo(2);
    }

    @Test
    void editionSimpleOrdinal() {
        ParsedDescription p = parser.parse("رقم الطبعة: الأولى");
        assertThat(p.editionNumber()).isEqualTo(1);
    }

    @Test
    void editionUnparseableOrdinal_returnsNull() {
        // нестандартная формулировка без распознаваемого ordinal/числа
        ParsedDescription p = parser.parse("الطبعة: طبعة منقحة");
        assertThat(p.editionNumber()).isNull();
    }

    @Test
    void arabicIndicDigits_volumes() {
        ParsedDescription p = parser.parse("عدد المجلدات: ٧");
        assertThat(p.volumes()).isEqualTo(7);
    }
}
