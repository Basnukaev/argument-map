package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.ai.LlmApiException;
import ru.basnukaev.argumentmap.ai.LlmClient;

/**
 * Unit-тесты для {@link BookMetadataExtractionService} (ADR-058). Без
 * Spring / БД / реального HTTP - {@link LlmClient} подменяется fake'ом,
 * возвращающим заранее заданный ответ. Проверяем: парсинг реального
 * описания (fmhji), tolerance к ```json fence, graceful fallback на
 * мусор / disabled / исключение.
 */
class BookMetadataExtractionServiceTest {

    private static final String FMHJI_DESCRIPTION =
            "<div><div align=\"right\">عنوان الكتاب: الفقه المنهجي على مذهب "
            + "الإمام الشافعي</div>المؤلف: مصطفى الخن ، مصطفى البغا ، علي "
            + "الشربجي<br/>الناشر: دار القلم دمشق<br/>سنة النشر: 1433 - 2012"
            + "<br/>عدد المجلدات : 3 <br/>رقم الطبعة :  الطبعة الثالثة عشر</div>";

    /**
     * Каноничный JSON-ответ который должен вернуть корректно
     * сработавший LLM на fmhji-описании.
     */
    private static final String FMHJI_JSON = """
            {
              "titleAr": "الفقه المنهجي على مذهب الإمام الشافعي",
              "authors": ["مصطفى الخن", "مصطفى البغا", "علي الشربجي"],
              "publisher": "دار القلم دمشق",
              "place": "دمشق",
              "editionText": "الطبعة الثالثة عشر",
              "editionNumber": 13,
              "yearHijri": 1433,
              "yearGregorian": 2012,
              "volumes": 3
            }""";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fake LlmClient - enabled, возвращает заданный canned response
     * на любой вызов complete.
     */
    private static LlmClient fakeReturning(String cannedResponse) {
        return new LlmClient() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return cannedResponse;
            }
        };
    }

    private static LlmClient disabledClient() {
        return new LlmClient() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("disabled - не должен вызываться");
            }
        };
    }

    private static LlmClient throwingClient() {
        return new LlmClient() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new LlmApiException("upstream down", 503);
            }
        };
    }

    @Test
    void extract_realFmhjiDescription_parsesAllFields() {
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(fakeReturning(FMHJI_JSON), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract(FMHJI_DESCRIPTION);

        assertThat(result).isPresent();
        ExtractedBookMetadata md = result.get();
        assertThat(md.titleAr()).isEqualTo("الفقه المنهجي على مذهب الإمام الشافعي");
        assertThat(md.authors())
                .containsExactly("مصطفى الخن", "مصطفى البغا", "علي الشربجي");
        assertThat(md.publisher()).isEqualTo("دار القلم دمشق");
        assertThat(md.place()).isEqualTo("دمشق");
        assertThat(md.editionText()).isEqualTo("الطبعة الثالثة عشر");
        assertThat(md.editionNumber()).isEqualTo(13);
        assertThat(md.yearHijri()).isEqualTo(1433);
        assertThat(md.yearGregorian()).isEqualTo(2012);
        assertThat(md.volumes()).isEqualTo(3);
    }

    @Test
    void extract_jsonFencedOutput_stillParses() {
        String fenced = "```json\n" + FMHJI_JSON + "\n```";
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(fakeReturning(fenced), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract(FMHJI_DESCRIPTION);

        assertThat(result).isPresent();
        assertThat(result.get().volumes()).isEqualTo(3);
        assertThat(result.get().yearHijri()).isEqualTo(1433);
    }

    @Test
    void extract_editionNumberAsString_coercedToInt() {
        String json = "{\"authors\":[],\"editionNumber\":\"13\",\"volumes\":\"3\"}";
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(fakeReturning(json), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract("some desc");

        assertThat(result).isPresent();
        assertThat(result.get().editionNumber()).isEqualTo(13);
        assertThat(result.get().volumes()).isEqualTo(3);
    }

    @Test
    void extract_nullableFieldsMissing_returnNull() {
        String json = "{\"authors\":[]}";
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(fakeReturning(json), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract("desc");

        assertThat(result).isPresent();
        ExtractedBookMetadata md = result.get();
        assertThat(md.titleAr()).isNull();
        assertThat(md.publisher()).isNull();
        assertThat(md.editionNumber()).isNull();
        assertThat(md.yearHijri()).isNull();
        assertThat(md.authors()).isEmpty();
    }

    @Test
    void extract_garbageResponse_returnsEmpty() {
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(
                        fakeReturning("это не json вовсе, просто текст"), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract(FMHJI_DESCRIPTION);

        assertThat(result).isEmpty();
    }

    @Test
    void extract_jsonArrayInsteadOfObject_returnsEmpty() {
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(fakeReturning("[1,2,3]"), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract("desc");

        assertThat(result).isEmpty();
    }

    @Test
    void extract_disabledClient_returnsEmptyWithoutCall() {
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(disabledClient(), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract(FMHJI_DESCRIPTION);

        assertThat(result).isEmpty();
    }

    @Test
    void extract_llmThrows_returnsEmpty() {
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(throwingClient(), objectMapper);

        Optional<ExtractedBookMetadata> result = service.extract(FMHJI_DESCRIPTION);

        assertThat(result).isEmpty();
    }

    @Test
    void extract_blankDescription_returnsEmpty() {
        BookMetadataExtractionService service =
                new BookMetadataExtractionService(fakeReturning(FMHJI_JSON), objectMapper);

        assertThat(service.extract("   ")).isEmpty();
        assertThat(service.extract(null)).isEmpty();
    }
}
