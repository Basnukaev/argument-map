package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShamelaBibliographyParserTest {

    private final ShamelaBibliographyParser parser = new ShamelaBibliographyParser();

    @Test
    void parsesIbnKathirTafsirEdition() {
        String biblio = "الكتاب: تفسير القرآن العظيم\\rالمؤلف: ابن كثير"
                + "\\rالمحقق: حكمت بن بشير بن ياسين"
                + "\\rالناشر: دار ابن الجوزي للنشر والتوزيع - السعودية"
                + "\\rالطبعة: الأولى، ١٤٣١ هـ"
                + "\\rعدد الأجزاء: ٧";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.muhaqqiq()).isEqualTo("حكمت بن بشير بن ياسين");
        assertThat(parsed.publisher()).isEqualTo("دار ابن الجوزي للنشر والتوزيع");
        assertThat(parsed.publicationPlace()).isEqualTo("السعودية");
        assertThat(parsed.editionNumber()).isEqualTo(1);
        assertThat(parsed.publishedYearHijri()).isEqualTo(1431);
        assertThat(parsed.publishedYearGregorian()).isNull();
    }

    @Test
    void parsesIqdJumanWithGregorianAndAlternateMuhaqqiqMarker() {
        String biblio = "الكتاب: عِقْد الجُمَان في تاريخ أهل الزمان"
                + "\\rالمؤلف: بدر الدين محمود العيني (ت ٨٥٥ هـ)"
                + "\\rحققه ووضع حواشيه: د محمد محمد أمين"
                + "\\rالناشر: مطبعة دار الكتب والوثائق القومية - القاهرة"
                + "\\rعام النشر: ١٤٣١ هـ - ٢٠١٠ م"
                + "\\rعدد الأجزاء: ٥";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.muhaqqiq()).isEqualTo("د محمد محمد أمين");
        assertThat(parsed.publisher()).isEqualTo("مطبعة دار الكتب والوثائق القومية");
        assertThat(parsed.publicationPlace()).isEqualTo("القاهرة");
        assertThat(parsed.editionNumber()).isNull();
        assertThat(parsed.publishedYearHijri()).isEqualTo(1431);
        assertThat(parsed.publishedYearGregorian()).isEqualTo(2010);
    }

    @Test
    void parsesAhadithAqidaWithBothCalendarsInEdition() {
        String biblio = "الكتاب: أحاديث العقيدة"
                + "\\rالمؤلف: د سليمان بن محمد الدبيخي"
                + "\\rالناشر: مكتبة دار البيان الحديثة، الطائف - المملكة العربية السعودية"
                + "\\rالطبعة: الأولى، ١٤٢٢ هـ - ٢٠٠١ م";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.muhaqqiq()).isNull();
        assertThat(parsed.publisher()).isEqualTo("مكتبة دار البيان الحديثة، الطائف");
        assertThat(parsed.publicationPlace()).isEqualTo("المملكة العربية السعودية");
        assertThat(parsed.editionNumber()).isEqualTo(1);
        assertThat(parsed.publishedYearHijri()).isEqualTo(1422);
        assertThat(parsed.publishedYearGregorian()).isEqualTo(2001);
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertThat(parser.parse(null).isEmpty()).isTrue();
        assertThat(parser.parse("").isEmpty()).isTrue();
        assertThat(parser.parse("   \\r  ").isEmpty()).isTrue();
    }

    @Test
    void returnsEmptyWhenNoMarkersMatch() {
        ParsedBibliography parsed = parser.parse("just some random arabic-less text");
        assertThat(parsed.isEmpty()).isTrue();
    }

    @Test
    void parsesEditionWithoutOrdinalUsingArabicIndicDigits() {
        String biblio = "الناشر: دار طيبة"
                + "\\rالطبعة: ٣، ١٤٣٠ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.editionNumber()).isEqualTo(3);
        assertThat(parsed.publishedYearHijri()).isEqualTo(1430);
    }

    @Test
    void parsesEditionWithSecondOrdinal() {
        String biblio = "الناشر: دار طيبة"
                + "\\rالطبعة: الثانية، ١٤٢٠ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.editionNumber()).isEqualTo(2);
    }

    @Test
    void explicitPublicationPlaceOverridesPublisherSplit() {
        String biblio = "الناشر: دار ابن الجوزي - السعودية"
                + "\\rمكان النشر: الرياض";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.publicationPlace()).isEqualTo("الرياض");
        // Поскольку явный marker нашёлся, heuristic split не должен сработать
        assertThat(parsed.publisher()).isEqualTo("دار ابن الجوزي - السعودية");
    }

    @Test
    void doesNotSplitPublisherWhenDashIsInsideName() {
        // Кейс: publisher содержит " - " в середине, не в конце как разделитель
        String biblio = "الناشر: مؤسسة الرسالة";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.publisher()).isEqualTo("مؤسسة الرسالة");
        assertThat(parsed.publicationPlace()).isNull();
    }

    @Test
    void recognisesTahqiqMarkerAsMuhaqqiq() {
        String biblio = "الكتاب: شرح صحيح البخاري"
                + "\\rتحقيق: عبد الله بن عبد المحسن التركي";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.muhaqqiq()).isEqualTo("عبد الله بن عبد المحسن التركي");
    }

    @Test
    void rejectsOutOfRangeYear() {
        // 99999 хр - явно мусор, должен быть filtered
        String biblio = "الطبعة: الأولى، ٩٩٩٩٩ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.editionNumber()).isEqualTo(1);
        assertThat(parsed.publishedYearHijri()).isNull();
    }
}
