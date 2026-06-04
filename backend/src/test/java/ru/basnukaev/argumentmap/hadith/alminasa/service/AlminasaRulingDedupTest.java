package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaHadithMapper.RulingCandidate;

/**
 * Изолированный unit-тест дедупа рулингов (план 3, Task 4, решение 7):
 * embedded+index схлопываются по природному ключу (ruler_name, ruling_text,
 * book_name, page, volume); embedded приоритетен; разные ruler'ы — отдельно.
 */
class AlminasaRulingDedupTest {

    private static RulingCandidate embedded(String ruler, String text, String book,
                                            Integer page, Integer volume) {
        return new RulingCandidate(ruler, 256, text, book, page, volume,
                "{\"source\":\"embedded\"}");
    }

    private static RulingCandidate index(String ruler, String text, String book,
                                         Integer page, Integer volume) {
        return new RulingCandidate(ruler, 256, text, book, page, volume,
                "{\"source\":\"index\"}");
    }

    @Test
    void embedded_и_index_с_одним_ключом_схлопываются_embedded_приоритет() {
        List<RulingCandidate> result = AlminasaHadithMapper.dedupRulings(List.of(
                embedded("البخاري", "أورده في صحيحه", "صحيح البخاري", 6, 1),
                index("البخاري", "أورده في صحيحه", "صحيح البخاري", 6, 1)));

        assertThat(result).hasSize(1);
        // embedded шёл первым → его metadata победила
        assertThat(result.get(0).metadata()).contains("embedded");
    }

    @Test
    void разные_ruler_не_схлопываются() {
        List<RulingCandidate> result = AlminasaHadithMapper.dedupRulings(List.of(
                embedded("البخاري", "أورده في صحيحه", "صحيح البخاري", 6, 1),
                index("مسلم", "أورده في صحيحه", "صحيح مسلم", 48, 6)));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RulingCandidate::rulerName)
                .containsExactly("البخاري", "مسلم");
    }

    @Test
    void разная_страница_того_же_ruler_не_схлопывается() {
        List<RulingCandidate> result = AlminasaHadithMapper.dedupRulings(List.of(
                embedded("البخاري", "أورده في صحيحه", "صحيح البخاري", 6, 1),
                index("البخاري", "أورده في صحيحه", "صحيح البخاري", 20, 1)));

        assertThat(result).hasSize(2);
    }
}
