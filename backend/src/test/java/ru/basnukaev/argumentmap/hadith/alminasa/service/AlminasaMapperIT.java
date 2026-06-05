package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithEditionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithExplanationRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRulingRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;
import ru.basnukaev.argumentmap.hadith.service.SanadGraphService;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphEdge;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse.GraphNode;

/**
 * e2e IT маппера alminasa staging→hd_* на фикстурах (план 3, Task 4): один
 * хадис 146-1 со всеми сателлитами + edge-кейсы (без цепи / пустой матн /
 * коллизия номера) + идемпотентность. Testcontainers PostgreSQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AlminasaMapperIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private AmHadithStagingDao hadithStagingDao;
    @Autowired private AmNarratorStagingDao narratorStagingDao;
    @Autowired private AmRulingStagingDao rulingStagingDao;
    @Autowired private AmExplanationStagingDao explanationStagingDao;

    @Autowired private AlminasaNarratorMapper narratorMapper;
    @Autowired private AlminasaHadithMapper hadithMapper;
    @Autowired private SanadGraphService sanadGraphService;

    @Autowired private CollectionRepository collectionRepository;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private MatnRepository matnRepository;
    @Autowired private HadithEditionRepository editionRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private HadithCrossrefRepository crossrefRepository;
    @Autowired private HadithRulingRepository rulingRepository;
    @Autowired private HadithExplanationRepository explanationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + name)) {
            return MAPPER.readTree(in);
        }
    }

    private static String norm(String s) {
        return ArabicTextNormalizer.normalize(s);
    }

    /** Засев staging для 146-1: hadith hit 0, narrator 5719, оба rulings-дока, explanation 146-1. */
    @BeforeEach
    void seed() throws IOException {
        JsonNode hadithHit0 = fixture("hadith-page.json").path("hits").path("hits").get(0);
        JsonNode src = hadithHit0.path("_source");
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-1", 146, 1L, "صحيح البخاري", "مرفوع",
                "باب بدء الوحي", "باب كيف كان بدء الوحي إلى رسول الله", src.toString())));

        JsonNode narratorSrc = fixture("narrators.json")
                .path("hits").path("hits").get(0).path("_source");
        narratorStagingDao.upsertAll(List.of(new AmNarratorRow(
                5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", narratorSrc.toString())));

        JsonNode rulingHits = fixture("rulings.json").path("hits").path("hits");
        for (JsonNode hit : rulingHits) {
            JsonNode rs = hit.path("_source");
            rulingStagingDao.upsertAll(List.of(new AmRulingRow(
                    hit.path("_id").asText(), rs.path("hadith_id").asText(),
                    rs.path("ruler").asText(), rs.path("ruler_dod").asInt(),
                    rs.path("narrations_type").asText(), rs.toString())));
        }

        JsonNode explHits = fixture("explanations.json").path("hits").path("hits");
        for (JsonNode hit : explHits) {
            JsonNode es = hit.path("_source");
            if ("146-1".equals(es.path("hadith").path("hadith_id").asText())) {
                explanationStagingDao.upsertAll(List.of(new AmExplanationRow(
                        hit.path("_id").asText(), "146-1",
                        es.path("explanation").path("explanation_book_name").asText(),
                        es.path("explanation").path("explanation_book_author").asText(),
                        es.toString())));
            }
        }
    }

    @Test
    void mapHadith_146_1_все_сателлиты() {
        narratorMapper.mapNarrator(narratorStagingDao.findById(5719).orElseThrow());
        UUID hadithId = hadithMapper.mapHadith(hadithStagingDao.findById("146-1").orElseThrow());

        // ── hadith ───────────────────────────────────────────────────────────────
        Hadith h = hadithRepository.findById(hadithId).orElseThrow();
        assertThat(h.externalSource()).isEqualTo("alminasa");
        assertThat(h.externalId()).isEqualTo("146-1");
        assertThat(h.status()).isEqualTo("CANONICAL"); // book 146
        assertThat(h.hadithType()).isEqualTo("مرفوع");
        assertThat(h.chapterAr()).isEqualTo("باب بدء الوحي");
        assertThat(h.fullTextAr()).contains("<a class=rawy id=4698>");
        assertThat(h.primaryNumber()).isEqualTo(1);
        // collection slug = bukhari
        Collection collection = collectionRepository.findById(h.collectionId()).orElseThrow();
        assertThat(collection.slug()).isEqualTo("bukhari");
        assertThat(collection.nameAr()).isEqualTo("صحيح البخاري");

        // ── matn ──────────────────────────────────────────────────────────────────
        List<Matn> matns = matnRepository.findByHadithId(hadithId);
        assertThat(matns).hasSize(1);
        Matn matn = matns.get(0);
        assertThat(matn.isPrimary()).isTrue();
        // сравнение в нормализованном пространстве: порядок combining-знаков в
        // фикстуре не NFC-канонический, литерал с диакритикой байт-в-байт не совпадёт
        assertThat(norm(matn.textAr())).startsWith(norm("إنما الأعمال بالنيات"));
        assertThat(matn.textArNormalized()).isNotBlank();
        assertThat(matn.printedNumber()).isEqualTo(1);
        assertThat(matn.pageNo()).isEqualTo(6);
        assertThat(matn.volume()).isEqualTo(1);

        // ── editions = 2 ───────────────────────────────────────────────────────────
        List<HadithEdition> editions = editionRepository.findByHadithId(hadithId);
        assertThat(editions).hasSize(2);

        // ── sanad: 6 звеньев, реверс, вектор формул ────────────────────────────────
        List<Sanad> sanads = sanadRepository.findByHadithId(hadithId);
        assertThat(sanads).hasSize(1);
        Sanad sanad = sanads.get(0);
        assertThat(sanad.primaryChain()).isTrue();
        // collectorPhrase в metadata цепи
        JsonNode sanadMeta = readJson(sanad.metadata());
        assertThat(sanadMeta.path("collectorPhrase").asText()).isEqualTo(norm("حدثنا"));

        List<SanadNarrator> links = sanadRepository.findNarratorsBySanadId(sanad.id());
        assertThat(links).hasSize(6);
        // position 0 = сподвижник 5913 (عمر, SAHABI)
        SanadNarrator pos0 = links.get(0);
        assertThat(pos0.position()).isZero();
        Narrator companion = narratorRepository.findById(pos0.narratorId()).orElseThrow();
        assertThat(companion.externalId()).isEqualTo("5913");
        assertThat(companion.reliabilityGrade()).isEqualTo(NarratorReliability.SAHABI);

        // ОЖИДАЕМЫЙ вектор формул по позициям (реш. 2, после реверса):
        //   receivedVia парсера 4698→حدثنا,3443→حدثنا,8272→أخبرني,6796→سمع,
        //   5719→سمعت,5913→سمعت → реверс → pos0=سمعت,…,pos5=حدثنا.
        List<String> expectedFormulas = List.of(
                norm("سمعت"),   // pos0 = 5913
                norm("سمعت"),   // pos1 = 5719
                norm("سمع"),    // pos2 = 6796
                norm("أخبرني"), // pos3 = 8272
                norm("حدثنا"),  // pos4 = 3443
                norm("حدثنا")); // pos5 = 4698
        assertThat(links).extracting(SanadNarrator::transmissionPhrase)
                .containsExactlyElementsOf(expectedFormulas);
        // соответствие external_id позициям
        assertThat(externalIdsByPosition(links))
                .containsExactly("5913", "5719", "6796", "8272", "3443", "4698");

        // ── round-trip SanadGraphService.buildGraph ────────────────────────────────
        SanadGraphResponse graph = sanadGraphService.buildGraph(hadithId);
        // присутствие Prophet-узла + 6 narrator-узлов
        assertThat(graph.nodes()).anySatisfy(n -> assertThat(n.role()).isEqualTo("PROPHET"));
        assertThat(graph.nodes()).filteredOn(n -> n.role().equals("COMPANION")).hasSize(1);
        // ребро Prophet→companion несёт label формулы сподвижника (سمعت)
        GraphNode prophet = graph.nodes().stream()
                .filter(n -> n.role().equals("PROPHET")).findFirst().orElseThrow();
        GraphEdge prophetEdge = graph.edges().stream()
                .filter(e -> e.source().equals(prophet.id())).findFirst().orElseThrow();
        assertThat(prophetEdge.data().transmissionPhrase()).isEqualTo(norm("سمعت"));
        // рёбер по числу пар (6 узлов + prophet → 6) + ребро к version-узлу
        // сборника в конце цепи (С58: цепь не обрывается в пустоту)
        assertThat(graph.edges()).hasSize(7);
        assertThat(graph.nodes()).anySatisfy(n -> {
            assertThat(n.role()).isEqualTo("VERSION");
            assertThat(n.version()).isNotNull();
            assertThat(n.version().externalId()).isEqualTo("146-1");
        });

        // ── crossrefs: без 146-1, с note-номерами ──────────────────────────────────
        List<HadithCrossref> crossrefs = crossrefRepository.findByHadithId(hadithId);
        assertThat(crossrefs).extracting(HadithCrossref::relatedExternalId)
                .doesNotContain("146-1");
        assertThat(crossrefs).allSatisfy(c -> assertThat(c.relationType()).isEqualTo("TARIQ"));
        assertThat(crossrefs).allSatisfy(c -> assertThat(c.relatedHadithId()).isNull());
        // note сиблинга 158-3537 → ["1907"]
        assertThat(crossrefs).anySatisfy(c -> {
            assertThat(c.relatedExternalId()).isEqualTo("158-3537");
            assertThat(c.note()).contains("1907");
        });

        // ── rulings: РОВНО 2 (Бухари page 6 vol 1 схлопнут; Муслим page 48 vol 6) ───
        List<HadithRuling> rulings = rulingRepository.findByHadithId(hadithId);
        assertThat(rulings).hasSize(2);
        assertThat(rulings).anySatisfy(r -> {
            assertThat(r.rulerName()).isEqualTo("البخاري");
            assertThat(r.page()).isEqualTo(6);
            assertThat(r.volume()).isEqualTo(1);
        });
        assertThat(rulings).anySatisfy(r -> {
            assertThat(r.rulerName()).isEqualTo("مسلم");
            assertThat(r.page()).isEqualTo(48);
            assertThat(r.volume()).isEqualTo(6);
            assertThat(readJson(r.metadata()).path("relatedExternalId").asText())
                    .isEqualTo("158-3537");
        });

        // ── explanations: 1×SHARH ──────────────────────────────────────────────────
        List<HadithExplanation> explanations = explanationRepository.findByHadithId(hadithId);
        assertThat(explanations).hasSize(1);
        HadithExplanation expl = explanations.get(0);
        assertThat(expl.kind()).isEqualTo("SHARH");
        assertThat(expl.text()).isNotBlank();
        assertThat(expl.bookName()).isEqualTo("فتح الباري بشرح صحيح البخاري");
        assertThat(expl.author()).isEqualTo("ابن حجر العسقلاني"); // trailing space обрезан
    }

    @Test
    void mapHadith_идемпотентен_counts_стабильны_uuid_тот_же() {
        narratorMapper.mapNarrator(narratorStagingDao.findById(5719).orElseThrow());
        AmHadithRow row = hadithStagingDao.findById("146-1").orElseThrow();
        UUID firstId = hadithMapper.mapHadith(row);
        long[] before = counts(firstId);

        UUID secondId = hadithMapper.mapHadith(row);
        long[] after = counts(secondId);

        assertThat(secondId).isEqualTo(firstId);
        assertThat(after).containsExactly(before);
        // ни одной дублированной строки
        assertThat(matnRepository.findByHadithId(firstId)).hasSize(1);
        assertThat(sanadRepository.findByHadithId(firstId)).hasSize(1);
    }

    @Test
    void mapHadith_без_rawy_тегов_импортируется_без_цепи() {
        // синтетический rawJson без rawy-тегов, но с матном
        String raw = "{\"hadith_id\":\"146-999\",\"book_name\":\"صحيح البخاري\","
                + "\"type\":\"مرفوع\",\"hadith\":\"متن بدون разметки رواة\","
                + "\"matn_with_tashkeel\":\"نص المتن\",\"number\":[999]}";
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-999", 146, 999L, "صحيح البخاري", "مرفوع", null, null, raw)));

        UUID id = hadithMapper.mapHadith(hadithStagingDao.findById("146-999").orElseThrow());

        assertThat(sanadRepository.findByHadithId(id)).isEmpty();
        assertThat(matnRepository.findByHadithId(id)).hasSize(1);
    }

    @Test
    void mapHadith_пустой_матн_бросает_исключение() {
        String raw = "{\"hadith_id\":\"146-998\",\"book_name\":\"صحيح البخاري\","
                + "\"type\":\"مرفوع\",\"hadith\":\"نص\",\"number\":[998]}";
        hadithStagingDao.upsertAll(List.of(new AmHadithRow(
                "146-998", 146, 998L, "صحيح البخاري", "مرفوع", null, null, raw)));

        AmHadithRow row = hadithStagingDao.findById("146-998").orElseThrow();
        assertThatThrownBy(() -> hadithMapper.mapHadith(row))
                .isInstanceOf(AlminasaMappingException.class);
    }

    @Test
    void mapHadith_коллизия_номера_второй_получает_null() {
        // оба хадиса одного сборника (146) с одинаковым primary_number=1
        String rawA = "{\"hadith_id\":\"146-1\",\"book_name\":\"صحيح البخاري\","
                + "\"type\":\"مرفوع\",\"hadith\":\"متن أ\",\"matn_with_tashkeel\":\"نص أ\",\"number\":[1]}";
        String rawB = "{\"hadith_id\":\"146-777\",\"book_name\":\"صحيح البخاري\","
                + "\"type\":\"مرفوع\",\"hadith\":\"متن ب\",\"matn_with_tashkeel\":\"نص ب\",\"number\":[1]}";
        hadithStagingDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1L, "صحيح البخاري", "مرفوع", null, null, rawA),
                new AmHadithRow("146-777", 146, 777L, "صحيح البخاري", "مرفوع", null, null, rawB)));

        UUID idA = hadithMapper.mapHadith(hadithStagingDao.findById("146-1").orElseThrow());
        UUID idB = hadithMapper.mapHadith(hadithStagingDao.findById("146-777").orElseThrow());

        assertThat(hadithRepository.findById(idA).orElseThrow().primaryNumber()).isEqualTo(1);
        assertThat(hadithRepository.findById(idB).orElseThrow().primaryNumber()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private List<String> externalIdsByPosition(List<SanadNarrator> links) {
        return links.stream()
                .map(l -> narratorRepository.findById(l.narratorId()).orElseThrow().externalId())
                .toList();
    }

    private long[] counts(UUID hadithId) {
        return new long[]{
                matnRepository.findByHadithId(hadithId).size(),
                editionRepository.findByHadithId(hadithId).size(),
                sanadRepository.findByHadithId(hadithId).size(),
                crossrefRepository.findByHadithId(hadithId).size(),
                rulingRepository.findByHadithId(hadithId).size(),
                explanationRepository.findByHadithId(hadithId).size()
        };
    }

    private JsonNode readJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
