package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.IsnadLink;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.ParsedIsnad;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Unit-тесты {@link AlminasaIsnadParser} на РЕАЛЬНОМ HTML из HAR-фикстуры
 * {@code hadith-page.json} (План 3 alminasa, Task 2, дизайн-решение 2 —
 * семантика «сегмент ПОСЛЕ тега»). Plain JUnit 5, без Spring.
 *
 * <p>Ожидаемые формулы пишутся через {@link ArabicTextNormalizer#normalize}, чтобы
 * не хардкодить нормализованную форму руками (أَخْبَرَنِي→اخبرني и т.п.).
 */
class AlminasaIsnadParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Поле {@code _source.hadith} N-го хита фикстуры — реальный HTML иснада. */
    private String hadithHtml(String fixture, int hitIndex) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + fixture)) {
            JsonNode resp = MAPPER.readTree(in);
            return resp.path("hits").path("hits").get(hitIndex).path("_source").path("hadith").asText();
        }
    }

    private static String norm(String token) {
        return ArabicTextNormalizer.normalize(token);
    }

    @Test
    void parse_146_1_шесть_звеньев_в_порядке_collector_companion() throws IOException {
        ParsedIsnad isnad = AlminasaIsnadParser.parse(hadithHtml("hadith-page.json", 0));

        // Порядок звеньев = порядок rawy-тегов = narrators[] (collector→companion).
        assertThat(isnad.links()).extracting(IsnadLink::externalId)
                .containsExactly("4698", "3443", "8272", "6796", "5719", "5913");

        // Формула составителя из сегмента ПЕРЕД первым тегом («حَدَّثَنَا»).
        assertThat(isnad.collectorPhrase()).isEqualTo(norm("حدثنا"));

        // Эталонный вектор формул «сегмент ПОСЛЕ тега» (дизайн-решение 2):
        //   4698→حدثنا, 3443→حدثنا, 8272→أخبرني, 6796→سمع, 5719→سمعت,
        //   5913→سمعت (хвост «…قَالَ : سَمِعْتُ رَسُولَ اللَّهِ…» — سمعت
        //   приоритетнее фолбэка قال).
        assertThat(isnad.links()).extracting(IsnadLink::receivedVia)
                .containsExactly(
                        norm("حدثنا"),
                        norm("حدثنا"),
                        norm("أخبرني"),
                        norm("سمع"),
                        norm("سمعت"),
                        norm("سمعت"));
    }

    @Test
    void parse_146_1_имена_из_тегов_тримятся() throws IOException {
        ParsedIsnad isnad = AlminasaIsnadParser.parse(hadithHtml("hadith-page.json", 0));

        // nameInText — содержимое тега (trim), падежная форма, для резолва НЕ источник.
        // Сравнение в нормализованном пространстве: порядок combining-знаков
        // (шадда/дамма) в фикстуре не NFC-канонический — литерал с диакритикой
        // байт-в-байт не совпадёт.
        assertThat(norm(isnad.links().get(0).nameInText()))
                .isEqualTo(norm("الحميدي عبد الله بن الزبير"));
        assertThat(norm(isnad.links().get(1).nameInText())).isEqualTo(norm("سفيان"));
        assertThat(isnad.links().get(0).nameInText()).doesNotStartWith(" ").doesNotEndWith(" ");
    }

    @Test
    void parse_146_53_количество_и_порядок_по_narrators() throws IOException {
        ParsedIsnad isnad = AlminasaIsnadParser.parse(hadithHtml("hadith-page.json", 1));

        // Из фикстуры (второй хит, _source.narrators[]): 6 звеньев, ids в порядке
        //   5085, 6659, 8272, 6796, 5719, 5913 — совпадает с порядком rawy-тегов.
        assertThat(isnad.links()).hasSize(6);
        assertThat(isnad.links()).extracting(IsnadLink::externalId)
                .containsExactly("5085", "6659", "8272", "6796", "5719", "5913");

        // Формулы 146-53: «حَدَّثَنَا … أَخْبَرَنَا … عَنْ ×4 … أَنَّ» (хвост «أَنَّ
        //   رَسُولَ اللَّهِ … قَالَ» — أن приоритетнее قال).
        assertThat(isnad.collectorPhrase()).isEqualTo(norm("حدثنا"));
        assertThat(isnad.links()).extracting(IsnadLink::receivedVia)
                .containsExactly(
                        norm("أخبرنا"),
                        norm("عن"),
                        norm("عن"),
                        norm("عن"),
                        norm("عن"),
                        norm("أن"));
    }

    @Test
    void parse_null_вход_пустой() {
        assertThat(AlminasaIsnadParser.parse(null)).isEqualTo(ParsedIsnad.empty());
    }

    @Test
    void parse_пустая_строка_пустой() {
        assertThat(AlminasaIsnadParser.parse("")).isEqualTo(ParsedIsnad.empty());
        assertThat(AlminasaIsnadParser.parse("   ")).isEqualTo(ParsedIsnad.empty());
    }

    @Test
    void parse_текст_без_rawy_тегов_пустой_список_и_null_формула() {
        ParsedIsnad isnad = AlminasaIsnadParser.parse("حَدَّثَنَا فلان عن فلان بدون разметки");

        assertThat(isnad.links()).isEmpty();
        assertThat(isnad.collectorPhrase()).isNull();
    }

    @Test
    void parse_тег_без_закрывающего_не_падает() {
        // Закрыт только первый тег; второй обрезан без </a> — парсер берёт то,
        // что распарсилось до незакрытого тега, и не бросает.
        String text = "حَدَّثَنَا <a class=rawy id=111>فلان</a> ، عَنْ <a class=rawy id=222>علان بدон закрытия";
        ParsedIsnad isnad = AlminasaIsnadParser.parse(text);

        assertThat(isnad.links()).extracting(IsnadLink::externalId).containsExactly("111");
        assertThat(isnad.links().get(0).receivedVia()).isEqualTo(norm("عن"));
        assertThat(isnad.collectorPhrase()).isEqualTo(norm("حدثنا"));
    }

    @Test
    void parse_сегмент_без_формулы_дает_null_receivedVia() {
        // Между тегами только мусор без формула-слова → receivedVia == null.
        String text = "<a class=rawy id=111>فلان</a> ، فلان فلان <a class=rawy id=222>علان</a>"
                + " <a class=matn>المتن</a>";
        ParsedIsnad isnad = AlminasaIsnadParser.parse(text);

        assertThat(isnad.links()).extracting(IsnadLink::externalId).containsExactly("111", "222");
        assertThat(isnad.links().get(0).receivedVia()).isNull();
    }

    @Test
    void parse_тег_с_пустым_именем_допустим() {
        String text = "حَدَّثَنَا <a class=rawy id=999></a> <a class=matn>المتن</a>";
        ParsedIsnad isnad = AlminasaIsnadParser.parse(text);

        assertThat(isnad.links()).hasSize(1);
        assertThat(isnad.links().get(0).externalId()).isEqualTo("999");
        assertThat(isnad.links().get(0).nameInText()).isEmpty();
    }

    @Test
    void parse_формула_сравнивается_равенством_а_не_substring() {
        // «عَنْهُ» (عنه после нормализации) НЕ матчится как «عَنْ» (عن) — сравнение
        // по равенству слова, а не по вхождению подстроки.
        String text = "<a class=rawy id=111>فلان</a> رَضِيَ اللَّهُ عَنْهُ <a class=matn>المتن</a>";
        ParsedIsnad isnad = AlminasaIsnadParser.parse(text);

        assertThat(isnad.links()).hasSize(1);
        assertThat(isnad.links().get(0).receivedVia()).isNull();
    }
}
