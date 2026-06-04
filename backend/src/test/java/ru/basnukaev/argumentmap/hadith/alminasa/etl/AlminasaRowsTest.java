package ru.basnukaev.argumentmap.hadith.alminasa.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;

/**
 * Unit-тесты фабрики {@link AlminasaRows} на реальных _source из HAR-фикстур
 * (test/resources/alminasa). План 2 alminasa, спека §B staging.
 */
class AlminasaRowsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AlminasaHit firstHit(String fixture) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + fixture)) {
            JsonNode resp = MAPPER.readTree(in);
            JsonNode hit = resp.path("hits").path("hits").get(0);
            return new AlminasaHit(hit.path("_id").asText(), hit.path("_source"), hit.path("sort"));
        }
    }

    @Test
    void fromHadithHit_парсит_горячие_поля_и_raw() throws IOException {
        AmHadithRow row = AlminasaRows.fromHadithHit(firstHit("hadith-page.json"));

        assertThat(row.hadithId()).isEqualTo("146-1");
        assertThat(row.bookId()).isEqualTo(146);
        assertThat(row.hadithSerialId()).isEqualTo(1L);
        assertThat(row.bookName()).isEqualTo("صحيح البخاري");
        assertThat(row.hadithType()).isEqualTo("مرفوع");
        assertThat(row.chapter()).isEqualTo("باب بدء الوحي");
        // raw — полный _source как JSON-строка (jsonb-колонка)
        assertThat(MAPPER.readTree(row.rawJson()).path("matn_with_tashkeel").asText()).isNotBlank();
    }

    @Test
    void fromHadithHit_кривой_hadith_id_бросает() {
        JsonNode source = MAPPER.createObjectNode()
                .put("hadith_id", "no-dash-but-not-number")
                .put("hadith_serial_id", 5);
        assertThatThrownBy(() -> AlminasaRows.fromHadithHit(new AlminasaHit("x", source, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-dash-but-not-number");
    }

    @Test
    void fromNarratorHit_id_из_ES_id_хита() throws IOException {
        AmNarratorRow row = AlminasaRows.fromNarratorHit(firstHit("narrators.json"));

        assertThat(row.narratorId()).isEqualTo(5719L);
        assertThat(row.fullName()).isEqualTo("علقمة بن وقاص العتواري");
        assertThat(row.grade()).isEqualTo("ثقة ثبت");
        assertThat(row.level()).isEqualTo("الثانية");
    }

    @Test
    void fromExplanationHit_hadith_id_из_вложенного_hadith() throws IOException {
        AmExplanationRow row = AlminasaRows.fromExplanationHit(firstHit("explanations.json"));

        assertThat(row.esId()).isEqualTo("GqPGhpEBXUur4f6nXKde");
        assertThat(row.hadithId()).isEqualTo("146-1");
        assertThat(row.bookName()).isEqualTo("فتح الباري بشرح صحيح البخاري");
        assertThat(row.author()).isEqualTo("ابن حجر العسقلاني"); // trailing space из источника тримится
    }

    @Test
    void fromRulingHit_парсит_ruler_и_dod() throws IOException {
        AmRulingRow row = AlminasaRows.fromRulingHit(firstHit("rulings.json"));

        assertThat(row.hadithId()).isEqualTo("146-1");
        assertThat(row.ruler()).isEqualTo("البخاري");
        assertThat(row.rulerDod()).isEqualTo(256);
        assertThat(row.narrationsType()).isEqualTo("raw");
    }
}
