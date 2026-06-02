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
    void splitsShortPublisherFromLongCountryName() {
        // Регрессия: короткий издатель «دار طيبة» + длинное название страны
        // «المملكة العربية السعودية» после « - ». Старый char-length-ratio
        // guard НЕ резал (24 ≥ 18) - место издания молча оставалось приклеенным
        // к publisher. Word-count guard (≤5 слов) разделяет корректно.
        String biblio = "الناشر: دار طيبة - المملكة العربية السعودية"
                + "\\rالطبعة: الأولى، ١٤٣٠ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.publisher()).isEqualTo("دار طيبة");
        assertThat(parsed.publicationPlace()).isEqualTo("المملكة العربية السعودية");
        assertThat(parsed.editionNumber()).isEqualTo(1);
        assertThat(parsed.publishedYearHijri()).isEqualTo(1430);
    }

    @Test
    void doesNotSplitPublisherWhenTailIsLongClause() {
        // Защита word-count guard: хвост после « - » из >5 слов - не топоним,
        // а часть имени/клаузы издателя → НЕ режем (место остаётся null).
        String biblio = "الناشر: دار النشر - للطباعة والنشر والتوزيع وكل ما يتعلق بذلك من خدمات";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.publisher())
                .isEqualTo("دار النشر - للطباعة والنشر والتوزيع وكل ما يتعلق بذلك من خدمات");
        assertThat(parsed.publicationPlace()).isNull();
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
    void parsesRealCarriageReturnSeparator() {
        // Production-БД хранит CR character chr(13), не literal "\r".
        // Парсер должен ловить оба формата через regex alternation.
        String biblio = "الكتاب: تفسير القرآن العظيم\r"
                + "المؤلف: ابن كثير\r"
                + "المحقق: حكمت بن بشير بن ياسين\r"
                + "الناشر: دار ابن الجوزي للنشر والتوزيع - السعودية\r"
                + "الطبعة: الأولى، ١٤٣١ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.muhaqqiq()).isEqualTo("حكمت بن بشير بن ياسين");
        assertThat(parsed.publisher()).isEqualTo("دار ابن الجوزي للنشر والتوزيع");
        assertThat(parsed.publicationPlace()).isEqualTo("السعودية");
        assertThat(parsed.editionNumber()).isEqualTo(1);
        assertThat(parsed.publishedYearHijri()).isEqualTo(1431);
    }

    @Test
    void rejectsOutOfRangeYear() {
        // 99999 хр - явно мусор, должен быть filtered
        String biblio = "الطبعة: الأولى، ٩٩٩٩٩ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.editionNumber()).isEqualTo(1);
        assertThat(parsed.publishedYearHijri()).isNull();
    }

    @Test
    void parsesMastersThesis_book15() {
        // Реальная «بطاقة الكتاب» книги 15 (магистерская диссертация).
        // degree+institution из строки رسالة, supervisor из إشراف, год из
        // العام الجامعي. publisher/muhaqqiq отсутствуют - это не изданная книга.
        String biblio = "الكتاب: الاستنباط عند الخطيب الشربيني (٩٧٧ هـ) في تفسيره السراج المنير - جمعاً ودراسة"
                + "\\rرسالة: ماجستير، جامعة الإمام محمد بن سعود الإسلامية - كلية أصول الدين - قسم القرآن وعلومه"
                + "\\rإعداد: أسماء بنت محمد بن عبدالعزيز الناصر"
                + "\\rإشراف: د عبدالعزيز بن ناصر السبر"
                + "\\rالعام الجامعي: ١٤٣٧ - ١٤٣٨ هـ"
                + "\\rعدد الصفحات: ٨٧١";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.thesisDegree()).isEqualTo("ماجستير");
        assertThat(parsed.thesisInstitution())
                .isEqualTo("جامعة الإمام محمد بن سعود الإسلامية - كلية أصول الدين - قسم القرآن وعلومه");
        assertThat(parsed.thesisSupervisor()).isEqualTo("د عبدالعزيز بن ناصر السبر");
        // Академический год → hijri (берётся последний/максимальный матч هـ)
        assertThat(parsed.publishedYearHijri()).isIn(1437, 1438);
        // Не изданная книга - publisher/muhaqqiq/edition отсутствуют
        assertThat(parsed.publisher()).isNull();
        assertThat(parsed.muhaqqiq()).isNull();
        assertThat(parsed.editionNumber()).isNull();
    }

    @Test
    void parsesThesis_dashSeparator_noLeadingDashOnInstitution() {
        // Если рисала разделяет degree/institution через « - » (а не «،»),
        // institution не должен получить ведущий «-» (весь разделитель
        // срезается, не только пробел).
        String biblio = "رسالة: دكتوراه - جامعة أم القرى - كلية الشريعة";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.thesisDegree()).isEqualTo("دكتوراه");
        assertThat(parsed.thesisInstitution()).isEqualTo("جامعة أم القرى - كلية الشريعة");
        // ведущего дефиса нет
        assertThat(parsed.thesisInstitution()).doesNotStartWith("-");
    }

    @Test
    void publishedBookHasNoThesisFields_noFalsePositives() {
        // Обычная изданная книга (ابن كثير) НЕ должна получить thesis-поля.
        String biblio = "الكتاب: تفسير القرآن العظيم\\rالمؤلف: ابن كثير"
                + "\\rالمحقق: حكمت بن بشير بن ياسين"
                + "\\rالناشر: دار ابن الجوزي للنشر والتوزيع - السعودية"
                + "\\rالطبعة: الأولى، ١٤٣١ هـ";

        ParsedBibliography parsed = parser.parse(biblio);

        assertThat(parsed.thesisDegree()).isNull();
        assertThat(parsed.thesisSupervisor()).isNull();
        assertThat(parsed.thesisInstitution()).isNull();
        // structured-поля по-прежнему парсятся
        assertThat(parsed.muhaqqiq()).isEqualTo("حكمت بن بشير بن ياسين");
    }
}
