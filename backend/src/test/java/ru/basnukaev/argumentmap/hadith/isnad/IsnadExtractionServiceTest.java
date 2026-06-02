package ru.basnukaev.argumentmap.hadith.isnad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.ai.LlmApiException;
import ru.basnukaev.argumentmap.ai.LlmClient;

/**
 * Unit-тесты для {@link IsnadExtractionService} (ADR-059). Без
 * Spring / БД / HTTP — {@link LlmClient} подменяется fake'ом. Проверяем:
 * парсинг иснада реального хадиса Бухари №1 (порядок передатчиков,
 * сподвижник = Умар ибн аль-Хаттаб последним, cleanedMatn), tolerance к
 * ```json fence, graceful fallback на disabled / мусор.
 */
class IsnadExtractionServiceTest {

    /** Реальный матн хадиса Бухари №1 («إنما الأعمال بالنيات») с иснадом. */
    private static final String BUKHARI_1_MATN =
            "حَدَّثَنَا الْحُمَيْدِيُّ عَبْدُ اللَّهِ بْنُ الزُّبَيْرِ، قَالَ حَدَّثَنَا "
            + "سُفْيَانُ، قَالَ حَدَّثَنَا يَحْيَى بْنُ سَعِيدٍ الأَنْصَارِيُّ، قَالَ "
            + "أَخْبَرَنِي مُحَمَّدُ بْنُ إِبْرَاهِيمَ التَّيْمِيُّ، أَنَّهُ سَمِعَ "
            + "عَلْقَمَةَ بْنَ وَقَّاصٍ اللَّيْثِيَّ، يَقُولُ سَمِعْتُ عُمَرَ بْنَ "
            + "الْخَطَّابِ ... إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ ...";

    /**
     * Каноничный JSON-ответ корректно сработавшего LLM на иснаде
     * Бухари №1: 6 передатчиков top→companion, сподвижник Умар последним.
     */
    private static final String BUKHARI_1_JSON = """
            {
              "isnadFound": true,
              "narrators": [
                {"name": "عبد الله بن الزبير الحميدي", "transmission": "حدثنا"},
                {"name": "سفيان", "transmission": "حدثنا"},
                {"name": "يحيى بن سعيد الأنصاري", "transmission": "أخبرني"},
                {"name": "محمد بن إبراهيم التيمي", "transmission": "أنه سمع"},
                {"name": "علقمة بن وقاص الليثي", "transmission": "سمعت"},
                {"name": "عمر بن الخطاب", "transmission": "عن النبي"}
              ],
              "cleanedMatn": "إنما الأعمال بالنيات"
            }""";

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                throw new IllegalStateException("disabled — не должен вызываться");
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
    void extract_disabledClient_returnsEmpty() {
        IsnadExtractionService service =
                new IsnadExtractionService(disabledClient(), objectMapper);

        assertThat(service.extract(BUKHARI_1_MATN)).isEmpty();
        assertThat(service.isLlmEnabled()).isFalse();
    }

    @Test
    void extract_bukhari1_parsesNarratorsInOrder() {
        IsnadExtractionService service =
                new IsnadExtractionService(fakeReturning(BUKHARI_1_JSON), objectMapper);

        Optional<ExtractedIsnad> result = service.extract(BUKHARI_1_MATN);

        assertThat(result).isPresent();
        ExtractedIsnad isnad = result.get();
        assertThat(isnad.isnadFound()).isTrue();
        assertThat(isnad.narrators()).hasSize(6);
        // narrators[0] — прямой источник составителя (верх матна)
        assertThat(isnad.narrators().get(0).name()).isEqualTo("عبد الله بن الزبير الحميدي");
        assertThat(isnad.narrators().get(0).transmission()).isEqualTo("حدثنا");
        // narrators[last] — сподвижник Умар ибн аль-Хаттаб
        assertThat(isnad.narrators().get(5).name()).isEqualTo("عمر بن الخطاب");
        assertThat(isnad.narrators().get(5).transmission()).isEqualTo("عن النبي");
        assertThat(isnad.cleanedMatn()).isEqualTo("إنما الأعمال بالنيات");
    }

    @Test
    void extract_jsonFencedOutput_stillParses() {
        String fenced = "```json\n" + BUKHARI_1_JSON + "\n```";
        IsnadExtractionService service =
                new IsnadExtractionService(fakeReturning(fenced), objectMapper);

        Optional<ExtractedIsnad> result = service.extract(BUKHARI_1_MATN);

        assertThat(result).isPresent();
        assertThat(result.get().narrators()).hasSize(6);
        assertThat(result.get().narrators().get(5).name()).isEqualTo("عمر بن الخطاب");
    }

    @Test
    void extract_garbageResponse_returnsEmpty() {
        IsnadExtractionService service = new IsnadExtractionService(
                fakeReturning("это не json вовсе, просто текст"), objectMapper);

        assertThat(service.extract(BUKHARI_1_MATN)).isEmpty();
    }

    @Test
    void extract_llmThrows_returnsEmpty() {
        IsnadExtractionService service =
                new IsnadExtractionService(throwingClient(), objectMapper);

        assertThat(service.extract(BUKHARI_1_MATN)).isEmpty();
    }

    @Test
    void extract_blankMatn_returnsEmpty() {
        IsnadExtractionService service =
                new IsnadExtractionService(fakeReturning(BUKHARI_1_JSON), objectMapper);

        assertThat(service.extract("   ")).isEmpty();
        assertThat(service.extract(null)).isEmpty();
    }

    @Test
    void extract_isnadFoundFalse_returnsNotFound() {
        String json = "{\"isnadFound\": false, \"narrators\": [], \"cleanedMatn\": null}";
        IsnadExtractionService service =
                new IsnadExtractionService(fakeReturning(json), objectMapper);

        Optional<ExtractedIsnad> result = service.extract(BUKHARI_1_MATN);

        assertThat(result).isPresent();
        assertThat(result.get().isnadFound()).isFalse();
        assertThat(result.get().narrators()).isEmpty();
    }

    @Test
    void extract_emptyNarrators_treatedAsNotFound() {
        // LLM выставил isnadFound=true, но цепь пуста — бесполезно, found=false
        String json = "{\"isnadFound\": true, \"narrators\": [], \"cleanedMatn\": \"x\"}";
        IsnadExtractionService service =
                new IsnadExtractionService(fakeReturning(json), objectMapper);

        Optional<ExtractedIsnad> result = service.extract(BUKHARI_1_MATN);

        assertThat(result).isPresent();
        assertThat(result.get().isnadFound()).isFalse();
    }
}
