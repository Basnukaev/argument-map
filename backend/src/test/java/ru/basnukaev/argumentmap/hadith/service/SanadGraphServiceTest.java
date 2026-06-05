package ru.basnukaev.argumentmap.hadith.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
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
 * синтетический корень Пророка ﷺ, роли и version-узел самого хадиса.
 */
class SanadGraphServiceTest {

    private final SanadRepository sanadRepository = mock(SanadRepository.class);
    private final NarratorRepository narratorRepository = mock(NarratorRepository.class);
    private final HadithRepository hadithRepository = mock(HadithRepository.class);
    private final MatnRepository matnRepository = mock(MatnRepository.class);
    private final CollectionRepository collectionRepository = mock(CollectionRepository.class);
    private final HadithCrossrefRepository crossrefRepository = mock(HadithCrossrefRepository.class);
    private final SanadGraphService service = new SanadGraphService(
            sanadRepository, narratorRepository, hadithRepository, matnRepository,
            collectionRepository, crossrefRepository, new ObjectMapper());

    private static Narrator narrator(UUID id, String name, String grade, String metadata) {
        return new Narrator(id, null, name, name, null, null, null, 100,
                null, null, null, grade, null, 0, metadata, Instant.EPOCH);
    }

    private static Sanad sanad(UUID id, UUID hadithId, UUID collectorId, boolean primary, String metadata) {
        return new Sanad(id, hadithId, "SAHIH", collectorId, null, primary, metadata, Instant.EPOCH);
    }

    private static Hadith hadith(UUID id, UUID collectionId, Integer number, String externalId) {
        return new Hadith(id, collectionId, number, "متن", "CANONICAL", null, null,
                Instant.EPOCH, "alminasa", externalId, null, null, null, null);
    }

    @Test
    void buildGraph_dedupesSharedStrand_andForksAtCommonLink() {
        UUID hadithId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        when(hadithRepository.findById(hadithId)).thenReturn(Optional.of(
                hadith(hadithId, collectionId, 146, "146-1")));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(
                new Collection(collectionId, "bukhari", "صحيح البخاري", null,
                        "Сахих аль-Бухари", null, null, null, Instant.EPOCH)));
        when(matnRepository.findPrimaryTextByHadithIds(List.of(hadithId)))
                .thenReturn(Map.of(hadithId, "إنما الأعمال بالنيات"));

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

        // Узлы: Пророк ﷺ + A + B + C + D + version = 6 (A,B общие, дедуп)
        assertEquals(6, graph.nodes().size());
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

        // version-узел: data=null, VersionInfo заполнен сборником/номером/превью
        GraphNode version = node(graph, "version-" + hadithId);
        assertEquals("VERSION", version.role());
        assertNull(version.data());
        assertNotNull(version.version());
        assertEquals(hadithId, version.version().hadithId());
        assertEquals("146-1", version.version().externalId());
        assertEquals("bukhari", version.version().collectionSlug());
        assertEquals("Сахих аль-Бухари", version.version().collectionNameRu());
        assertEquals(146, version.version().printedNumber());
        assertEquals("إنما الأعمال بالنيات", version.version().matnPreview());

        // Рёбра передачи: prophet→A, A→B (оба общие), B→C, B→D = 4
        // + version-рёбра от коллекторных концов: C→version, D→version = 2
        assertEquals(6, graph.edges().size());

        GraphEdge prophetToA = edge(graph, "prophet", "narrator-" + a);
        assertEquals(2, prophetToA.data().sanadCount()); // обе цепи проходят
        assertTrue(prophetToA.data().onPrimaryChain());
        // подпись общего ребра — из primary chain (s1: سمعت)
        assertEquals("سمعت", prophetToA.data().transmissionPhrase());

        // fan-out у B: два разных исходящих ребра
        assertNotNull(edge(graph, "narrator-" + b, "narrator-" + c));
        assertNotNull(edge(graph, "narrator-" + b, "narrator-" + d));

        // version-рёбра: от C (primary-цепь) и от D (вторичная)
        GraphEdge cToVersion = edge(graph, "narrator-" + c, "version-" + hadithId);
        assertNull(cToVersion.data().transmissionPhrase());
        assertTrue(cToVersion.data().onPrimaryChain());
        GraphEdge dToVersion = edge(graph, "narrator-" + d, "version-" + hadithId);
        assertTrue(!dToVersion.data().onPrimaryChain());

        assertEquals(2, graph.sanads().size());
    }

    @Test
    void buildTuruqGraph_mergesSiblings_sharingNarratorNodes() {
        UUID mainId = UUID.randomUUID();
        UUID sibId = UUID.randomUUID();
        UUID collMain = UUID.randomUUID();
        UUID collSib = UUID.randomUUID();
        // Общий сподвижник A (тот же UUID между версиями), разные коллекторы.
        UUID a = UUID.randomUUID();
        UUID cMain = UUID.randomUUID();
        UUID cSib = UUID.randomUUID();
        UUID sMain = UUID.randomUUID();
        UUID sSib = UUID.randomUUID();

        when(hadithRepository.findById(mainId)).thenReturn(Optional.of(
                hadith(mainId, collMain, 1, "146-1")));
        when(hadithRepository.findById(sibId)).thenReturn(Optional.of(
                hadith(sibId, collSib, 2, "158-2")));
        when(collectionRepository.findById(collMain)).thenReturn(Optional.of(
                new Collection(collMain, "bukhari", "صحيح البخاري", null,
                        "Сахих аль-Бухари", null, null, null, Instant.EPOCH)));
        when(collectionRepository.findById(collSib)).thenReturn(Optional.of(
                new Collection(collSib, "muslim", "صحيح مسلم", null,
                        "Сахих Муслима", null, null, null, Instant.EPOCH)));
        when(matnRepository.findPrimaryTextByHadithIds(List.of(mainId)))
                .thenReturn(Map.of(mainId, "متن الأصل"));
        when(matnRepository.findPrimaryTextByHadithIds(List.of(sibId)))
                .thenReturn(Map.of(sibId, "متن الطريق"));

        // crossref главного → резолвленный сиблинг.
        when(crossrefRepository.findByHadithId(mainId)).thenReturn(List.of(
                new HadithCrossref(UUID.randomUUID(), mainId, "158-2", sibId,
                        "TARIQ", null, Instant.EPOCH)));

        // main: A → cMain ; sib: A → cSib (A общий)
        when(sanadRepository.findByHadithId(mainId)).thenReturn(List.of(
                sanad(sMain, mainId, cMain, true, null)));
        when(sanadRepository.findByHadithId(sibId)).thenReturn(List.of(
                sanad(sSib, sibId, cSib, true, null)));
        when(sanadRepository.findNarratorsBySanadIds(List.of(sMain))).thenReturn(List.of(
                new SanadNarrator(sMain, 0, a, "سمعت"),
                new SanadNarrator(sMain, 1, cMain, "حدثنا")));
        when(sanadRepository.findNarratorsBySanadIds(List.of(sSib))).thenReturn(List.of(
                new SanadNarrator(sSib, 0, a, "عن"),
                new SanadNarrator(sSib, 1, cSib, "حدثنا")));
        when(narratorRepository.findByIds(any())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return ids.stream().map(id -> narrator(id, "name-" + id,
                    id.equals(a) ? NarratorReliability.SAHABI : NarratorReliability.THIQA, null)).toList();
        });

        SanadGraphResponse graph = service.buildTuruqGraph(mainId);

        assertEquals(mainId, graph.hadithId());

        // Один общий узел сподвижника A (шарится между версиями).
        long aNodes = graph.nodes().stream()
                .filter(n -> n.id().equals("narrator-" + a)).count();
        assertEquals(1, aNodes);

        // Два version-узла (по одному на версию).
        long versionNodes = graph.nodes().stream()
                .filter(n -> "VERSION".equals(n.role())).count();
        assertEquals(2, versionNodes);
        assertNotNull(node(graph, "version-" + mainId));
        assertNotNull(node(graph, "version-" + sibId));

        // Ребро prophet→A агрегирует обе версии: sanadCount=2.
        GraphEdge prophetToA = edge(graph, "prophet", "narrator-" + a);
        assertEquals(2, prophetToA.data().sanadCount());

        // У каждой версии своё ребро от своего топ-звена в свой version-узел.
        assertNotNull(edge(graph, "narrator-" + cMain, "version-" + mainId));
        assertNotNull(edge(graph, "narrator-" + cSib, "version-" + sibId));

        // onPrimaryChain — только цепь главного: ребро A→cMain primary,
        // A→cSib не помечено.
        assertTrue(edge(graph, "narrator-" + a, "narrator-" + cMain).data().onPrimaryChain());
        assertTrue(!edge(graph, "narrator-" + a, "narrator-" + cSib).data().onPrimaryChain());

        // sanads[] содержит цепи обеих версий.
        assertEquals(2, graph.sanads().size());
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
