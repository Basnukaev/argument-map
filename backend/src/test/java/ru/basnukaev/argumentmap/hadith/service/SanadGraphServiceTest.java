package ru.basnukaev.argumentmap.hadith.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphEdge;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphNode;

/**
 * Unit-тест ядра сборки графа иснада (без Spring/БД, Mockito-моки репозиториев).
 *
 * <p>Фикстура: две цепи с общим верхним участком A→B, расходящиеся на двух
 * составителей C и D (fan-out у B). Проверяем дедупликацию узлов и рёбер,
 * синтетический корень Пророка ﷺ и роли.
 */
class SanadGraphServiceTest {

    private final SanadRepository sanadRepository = mock(SanadRepository.class);
    private final NarratorRepository narratorRepository = mock(NarratorRepository.class);
    private final SanadGraphService service =
            new SanadGraphService(sanadRepository, narratorRepository, new ObjectMapper());

    private static Narrator narrator(UUID id, String name, String grade, String metadata) {
        return new Narrator(id, null, name, name, null, null, null, 100,
                null, null, null, grade, null, 0, metadata, Instant.EPOCH);
    }

    private static Sanad sanad(UUID id, UUID hadithId, UUID collectorId, boolean primary, String metadata) {
        return new Sanad(id, hadithId, "SAHIH", collectorId, null, primary, metadata, Instant.EPOCH);
    }

    @Test
    void buildGraph_dedupesSharedStrand_andForksAtCommonLink() {
        UUID hadithId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        // primary chain (s1): A → B → C ; second chain (s2): A → B → D
        when(sanadRepository.findByHadithId(hadithId)).thenReturn(List.of(
                sanad(s1, hadithId, c, true, "{\"collectionRu\":\"Бухари\"}"),
                sanad(s2, hadithId, d, false, "{\"collectionRu\":\"Муслим\"}")
        ));
        when(sanadRepository.findNarratorsBySanadIds(any())).thenReturn(List.of(
                new SanadNarrator(s1, 0, a, "سمعت"),
                new SanadNarrator(s1, 1, b, "حدثنا"),
                new SanadNarrator(s1, 2, c, "حدثنا"),
                new SanadNarrator(s2, 0, a, "عن"),
                new SanadNarrator(s2, 1, b, "عن"),
                new SanadNarrator(s2, 2, d, "حدثنا")
        ));
        when(narratorRepository.findByIds(any())).thenReturn(List.of(
                narrator(a, "صحابي", NarratorReliability.SAHABI, null),
                narrator(b, "الراوي", NarratorReliability.THIQA, null),
                narrator(c, "البخاري", NarratorReliability.THIQA, "{\"nameRu\":\"аль-Бухари\"}"),
                narrator(d, "مسلم", NarratorReliability.THIQA, null)
        ));

        SanadGraphResponse graph = service.buildGraph(hadithId);

        // Узлы: Пророк ﷺ + A + B + C + D = 5 (A,B общие, дедуп)
        assertEquals(5, graph.nodes().size());
        GraphNode prophet = node(graph, "prophet");
        assertNotNull(prophet);
        assertEquals("PROPHET", prophet.role());

        assertEquals("COMPANION", node(graph, "narrator-" + a).role());
        assertEquals("NARRATOR", node(graph, "narrator-" + b).role());
        assertEquals("COLLECTOR", node(graph, "narrator-" + c).role());
        assertEquals("COLLECTOR", node(graph, "narrator-" + d).role());
        // collection прокидывается из metadata sanad'а на узел-составитель
        assertEquals("Бухари", node(graph, "narrator-" + c).data().collection());
        // nameRu из metadata narrator'а
        assertEquals("аль-Бухари", node(graph, "narrator-" + c).data().nameRu());

        // Рёбра дедуплицированы: prophet→A, A→B (оба общие), B→C, B→D = 4
        assertEquals(4, graph.edges().size());

        GraphEdge prophetToA = edge(graph, "prophet", "narrator-" + a);
        assertEquals(2, prophetToA.data().sanadCount()); // обе цепи проходят
        assertTrue(prophetToA.data().onPrimaryChain());
        // подпись общего ребра — из primary chain (s1: سمعت)
        assertEquals("سمعت", prophetToA.data().transmissionPhrase());

        // fan-out у B: два разных исходящих ребра
        assertNotNull(edge(graph, "narrator-" + b, "narrator-" + c));
        assertNotNull(edge(graph, "narrator-" + b, "narrator-" + d));

        assertEquals(2, graph.sanads().size());
    }

    @Test
    void buildGraphFromExtracted_inMemory_prophetCompanionRolesTiers() {
        // top→companion (как в матне): источник составителя … сподвижник
        ru.basnukaev.argumentmap.hadith.isnad.ExtractedIsnad isnad =
                new ru.basnukaev.argumentmap.hadith.isnad.ExtractedIsnad(
                        true,
                        List.of(
                                new ru.basnukaev.argumentmap.hadith.isnad.ExtractedNarrator(
                                        "الحميدي", "حدثنا"),
                                new ru.basnukaev.argumentmap.hadith.isnad.ExtractedNarrator(
                                        "سفيان", "حدثنا"),
                                new ru.basnukaev.argumentmap.hadith.isnad.ExtractedNarrator(
                                        "عمر بن الخطاب", "عن النبي")),
                        "إنما الأعمال بالنيات");

        SanadGraphResponse graph =
                service.buildGraphFromExtracted(isnad, "صحيح البخاري", "Сахих аль-Бухари");

        // hadithId null (превью), sanads пуст
        assertEquals(null, graph.hadithId());
        assertTrue(graph.sanads().isEmpty());

        // Узлы: Пророк ﷺ + 3 передатчика + COLLECTOR = 5
        assertEquals(5, graph.nodes().size());

        GraphNode prophet = node(graph, "prophet");
        assertNotNull(prophet);
        assertEquals("PROPHET", prophet.role());

        // position 0 (после реверса) = сподвижник Умар
        GraphNode companion = node(graph, "x-0");
        assertEquals("COMPANION", companion.role());
        assertEquals("عمر بن الخطاب", companion.data().nameAr());
        assertEquals(null, companion.data().narratorId());
        assertEquals(1, companion.data().tier());

        // верхние передатчики — NARRATOR (составитель НЕ звено цепи)
        assertEquals("NARRATOR", node(graph, "x-1").role());
        assertEquals("NARRATOR", node(graph, "x-2").role());
        assertEquals("سفيان", node(graph, "x-1").data().nameAr());
        assertEquals("الحميدي", node(graph, "x-2").data().nameAr());
        // тиры по возрастанию
        assertEquals(2, node(graph, "x-1").data().tier());
        assertEquals(3, node(graph, "x-2").data().tier());

        // COLLECTOR-узел снизу, tier = max+1
        GraphNode collector = node(graph, "collector");
        assertEquals("COLLECTOR", collector.role());
        assertEquals("صحيح البخاري", collector.data().nameAr());
        assertEquals(4, collector.data().tier());

        // Рёбра: prophet→x-0→x-1→x-2→collector = 4
        assertEquals(4, graph.edges().size());
        GraphEdge prophetToCompanion = edge(graph, "prophet", "x-0");
        assertEquals("عن النبي", prophetToCompanion.data().transmissionPhrase());
        assertTrue(prophetToCompanion.data().onPrimaryChain());
        assertEquals(1, prophetToCompanion.data().sanadCount());
        // transmission ребра = формула получателя (узла-target)
        assertEquals("حدثنا", edge(graph, "x-0", "x-1").data().transmissionPhrase());
        assertNotNull(edge(graph, "x-1", "x-2"));
        assertNotNull(edge(graph, "x-2", "collector"));
    }

    private static GraphNode node(SanadGraphResponse g, String id) {
        return g.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    private static GraphEdge edge(SanadGraphResponse g, String source, String target) {
        return g.edges().stream()
                .filter(e -> e.source().equals(source) && e.target().equals(target))
                .findFirst().orElseThrow();
    }
}
