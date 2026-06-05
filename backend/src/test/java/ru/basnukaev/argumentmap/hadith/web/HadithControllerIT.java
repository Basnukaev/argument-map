package ru.basnukaev.argumentmap.hadith.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithCrossref;
import ru.basnukaev.argumentmap.hadith.domain.HadithEdition;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
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

/**
 * IT для HadithController. Vision 49d Section 2.6 Phase 1.f/g.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HadithControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private SanadRepository sanadRepository;
    @Autowired private MatnRepository matnRepository;
    @Autowired private HadithEditionRepository editionRepository;
    @Autowired private HadithRulingRepository rulingRepository;
    @Autowired private HadithExplanationRepository explanationRepository;
    @Autowired private HadithCrossrefRepository crossrefRepository;
    @Autowired private CollectionRepository collectionRepository;

    private UUID hadithId;
    private UUID narratorId;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        // Полный (alminasa) конструктор: externalId/tabaqa/gradeText заполнены —
        // нужны для проверки sanad-graph externalId узла.
        Narrator narrator = new Narrator(
                UUID.randomUUID(), null, "أبو هريرة", "abu hurayrah",
                null, null, null, 59, null, null, null,
                NarratorReliability.THIQA, null, 0, null, now,
                "alminasa", "rawy-59", "صحابي", "صحابي جليل", null, null
        );
        narratorRepository.save(narrator);
        narratorId = narrator.id();

        Hadith hadith = new Hadith(
                UUID.randomUUID(), null, 1,
                "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now
        );
        hadithRepository.save(hadith);
        hadithId = hadith.id();

        Sanad sanad = new Sanad(
                UUID.randomUUID(), hadithId,
                "SAHIH", null, null, true, null, now
        );
        sanadRepository.save(sanad);
        sanadRepository.saveNarratorLink(
                new SanadNarrator(sanad.id(), 0, narratorId, "سمعت")
        );

        Matn matn = new Matn(
                UUID.randomUUID(), hadithId,
                "إنما الأعمال بالنيات", "innama al-amal",
                "Дела по намерениям", null,
                null, 1, null, null, true, null, null, now
        );
        matnRepository.save(matn);
    }

    @Test
    void GET_list_returnsHadiths() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void GET_one_returnsHadith() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hadithId.toString()))
                .andExpect(jsonPath("$.status").value("CANONICAL"));
    }

    @Test
    void GET_nonExistent_returns404() throws Exception {
        UUID ghost = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}", ghost))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("hadith-not-found")));
    }

    @Test
    void GET_detail_returnsBundledResponse() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hadithId.toString()))
                .andExpect(jsonPath("$.sanads").isArray())
                .andExpect(jsonPath("$.sanads.length()").value(1))
                .andExpect(jsonPath("$.sanads[0].chainGrade").value("SAHIH"))
                .andExpect(jsonPath("$.sanads[0].narrators.length()").value(1))
                .andExpect(jsonPath("$.sanads[0].narrators[0].narratorId").value(narratorId.toString()))
                .andExpect(jsonPath("$.sanads[0].narrators[0].position").value(0))
                .andExpect(jsonPath("$.matns").isArray())
                .andExpect(jsonPath("$.matns.length()").value(1))
                .andExpect(jsonPath("$.matns[0].isPrimary").value(true))
                .andExpect(jsonPath("$.grades").isArray());
    }

    @Test
    void GET_detail_legacyHadith_emptySatellitesAndNullAlminasaFields() throws Exception {
        // Хадис из setUp() сидирован legacy-конструктором (без alminasa-полей):
        // новые поля null, списки сателлитов пустые — legacy-рендер не ломается.
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hadithType").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.chapterAr").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.subChapterAr").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fullTextAr").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.editions").isArray())
                .andExpect(jsonPath("$.editions.length()").value(0))
                .andExpect(jsonPath("$.rulings.length()").value(0))
                .andExpect(jsonPath("$.explanations.length()").value(0))
                .andExpect(jsonPath("$.crossrefs.length()").value(0));
    }

    @Test
    void GET_detail_alminasaHadith_returnsEnrichedFields() throws Exception {
        Instant now = Instant.now();

        // alminasa-хадис: external_source='alminasa' + hadithType/chapterAr/fullTextAr.
        Hadith am = new Hadith(
                UUID.randomUUID(), null, 146,
                "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now,
                "alminasa", "146-1", "مرفوع",
                "كتاب بدء الوحي", "باب كيف كان بدء الوحي",
                "<a class=rawy id=1>عمر بن الخطاب</a> قال <a class=matn>إنما الأعمال بالنيات</a>"
        );
        hadithRepository.save(am);
        UUID amId = am.id();

        editionRepository.save(new HadithEdition(
                UUID.randomUUID(), amId, "طبعة بولاق", 12, 1));

        // metadata {"source":"embedded"} → ruling провенанс «на этот хадис».
        rulingRepository.save(new HadithRuling(
                UUID.randomUUID(), amId, "البخاري", 256,
                "صحيح", "الجامع الصحيح", 5, 1,
                "{\"source\":\"embedded\"}", now));

        explanationRepository.save(new HadithExplanation(
                UUID.randomUUID(), amId, "SHARH", "فتح الباري", "ابن حجر",
                852, 3, 1, "شرح الحديث...", null, now));

        // crossref на сиблинг сборника 158 (Муслим) с JSON-массивом номеров в note.
        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), amId, "158-99", null,
                "TARIQ", "[\"12\",\"13\"]", now));

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", amId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("146-1"))
                .andExpect(jsonPath("$.hadithType").value("مرفوع"))
                .andExpect(jsonPath("$.chapterAr").value("كتاب بدء الوحي"))
                .andExpect(jsonPath("$.subChapterAr").value("باب كيف كان بدء الوحي"))
                .andExpect(jsonPath("$.fullTextAr").value(org.hamcrest.Matchers.containsString("rawy")))
                .andExpect(jsonPath("$.editions.length()").value(1))
                .andExpect(jsonPath("$.editions[0].editionName").value("طبعة بولاق"))
                .andExpect(jsonPath("$.editions[0].page").value(12))
                .andExpect(jsonPath("$.rulings.length()").value(1))
                .andExpect(jsonPath("$.rulings[0].rulerName").value("البخاري"))
                .andExpect(jsonPath("$.rulings[0].rulerDeathYear").value(256))
                .andExpect(jsonPath("$.rulings[0].source").value("embedded"))
                .andExpect(jsonPath("$.rulings[0].relatedExternalId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rulings[0].relatedHadithId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.explanations.length()").value(1))
                .andExpect(jsonPath("$.explanations[0].kind").value("SHARH"))
                .andExpect(jsonPath("$.explanations[0].author").value("ابن حجر"))
                .andExpect(jsonPath("$.crossrefs.length()").value(1))
                .andExpect(jsonPath("$.crossrefs[0].relatedExternalId").value("158-99"))
                .andExpect(jsonPath("$.crossrefs[0].numbers").value(org.hamcrest.Matchers.contains("12", "13")))
                .andExpect(jsonPath("$.crossrefs[0].collectionNameRu").value("Сахих Муслима"))
                .andExpect(jsonPath("$.crossrefs[0].collectionNameAr").value("صحيح مسلم"));
    }

    @Test
    void GET_detail_rulingResolvesRelatedHadithId_andCollectionNameRu() throws Exception {
        Instant now = Instant.now();

        // Импортированный сиблинг сборника 158 (Муслим) — цель резолва.
        Hadith sibling = new Hadith(
                UUID.randomUUID(), null, 99,
                "متن الطريق", HadithStatus.VARIANT, null, null, now,
                "alminasa", "158-99", null, null, null, null);
        hadithRepository.save(sibling);

        // Хадис с index-вердиктом на параллельную передачу 158-99.
        Hadith am = new Hadith(
                UUID.randomUUID(), null, 1,
                "متن الأصل", HadithStatus.CANONICAL, null, null, now,
                "alminasa", "146-1", null, null, null, null);
        hadithRepository.save(am);
        UUID amId = am.id();

        rulingRepository.save(new HadithRuling(
                UUID.randomUUID(), amId, "الألباني", 1420,
                "صحيح", "السلسلة الصحيحة", 7, 1,
                "{\"source\":\"index\",\"relatedExternalId\":\"158-99\"}", now));

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/detail", amId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulings.length()").value(1))
                .andExpect(jsonPath("$.rulings[0].source").value("index"))
                .andExpect(jsonPath("$.rulings[0].relatedExternalId").value("158-99"))
                .andExpect(jsonPath("$.rulings[0].relatedHadithId").value(sibling.id().toString()))
                .andExpect(jsonPath("$.rulings[0].relatedCollectionNameRu").value("Сахих Муслима"));
    }

    @Test
    void GET_filterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths").param("status", "CANONICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CANONICAL"));
    }

    @Test
    void GET_sanadGraph_returnsProphetRootedGraph() throws Exception {
        // setUp создаёт 1 sanad с 1 narrator (position 0) - граф = Пророк ﷺ
        // (синтетический корень) + 1 узел-сподвижник + version-узел самого
        // хадиса: 3 узла, 2 ребра (prophet→narrator, narrator→version).
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", hadithId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hadithId").value(hadithId.toString()))
                .andExpect(jsonPath("$.nodes.length()").value(3))
                .andExpect(jsonPath("$.edges.length()").value(2))
                .andExpect(jsonPath("$.edges[?(@.source == 'prophet')].target")
                        .value(org.hamcrest.Matchers.hasItem("narrator-" + narratorId)))
                // alminasa-обогащение узла: externalId/tabaqa/gradeText для клик-резолва
                .andExpect(jsonPath("$.nodes[?(@.id == 'narrator-" + narratorId + "')].data.externalId")
                        .value(org.hamcrest.Matchers.hasItem("rawy-59")))
                .andExpect(jsonPath("$.nodes[?(@.id == 'narrator-" + narratorId + "')].data.tabaqa")
                        .value(org.hamcrest.Matchers.hasItem("صحابي")))
                .andExpect(jsonPath("$.sanads.length()").value(1));
    }

    @Test
    void GET_sanadGraph_appendsVersionNode_withCollectionAndPreview() throws Exception {
        Instant now = Instant.now();

        // Сборник с известным русским названием + alminasa-хадис с номером и матном.
        Collection coll = new Collection(
                UUID.randomUUID(), "bukhari", "صحيح البخاري", null,
                "Сахих аль-Бухари", null, null,
                "{\"source\":\"alminasa\"}", now);
        collectionRepository.save(coll);

        Hadith vHadith = new Hadith(
                UUID.randomUUID(), coll.id(), 1,
                "إنما الأعمال بالنيات",
                HadithStatus.CANONICAL, null, null, now,
                "alminasa", "146-7", "مرفوع", null, null, null);
        hadithRepository.save(vHadith);
        UUID vId = vHadith.id();

        Narrator companion = new Narrator(
                UUID.randomUUID(), null, "عمر", "umar",
                null, null, null, 23, null, null, null,
                NarratorReliability.SAHABI, null, 0, null, now,
                "alminasa", "rawy-1", null, null, null, null);
        narratorRepository.save(companion);
        Narrator collector = new Narrator(
                UUID.randomUUID(), null, "البخاري", "albukhari",
                null, null, null, 256, null, null, null,
                NarratorReliability.THIQA, null, 0, null, now,
                "alminasa", "rawy-2", null, null, null, null);
        narratorRepository.save(collector);

        Sanad sanad = new Sanad(UUID.randomUUID(), vId, "SAHIH", null, null, true, null, now);
        sanadRepository.save(sanad);
        sanadRepository.saveNarratorLink(new SanadNarrator(sanad.id(), 0, companion.id(), "سمعت"));
        sanadRepository.saveNarratorLink(new SanadNarrator(sanad.id(), 1, collector.id(), "حدثنا"));

        matnRepository.save(new Matn(
                UUID.randomUUID(), vId, "إنما الأعمال بالنيات", "innama",
                null, null, coll.id(), 1, null, null, true, null, null, now));

        String versionId = "version-" + vId;
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", vId))
                .andExpect(status().isOk())
                // version-узел: role=VERSION, data=null, VersionInfo заполнен
                .andExpect(jsonPath("$.nodes[?(@.id == '" + versionId + "')].role")
                        .value(org.hamcrest.Matchers.hasItem("VERSION")))
                .andExpect(jsonPath("$.nodes[?(@.id == '" + versionId + "')].version.collectionNameRu")
                        .value(org.hamcrest.Matchers.hasItem("Сахих аль-Бухари")))
                .andExpect(jsonPath("$.nodes[?(@.id == '" + versionId + "')].version.printedNumber")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.nodes[?(@.id == '" + versionId + "')].version.matnPreview")
                        .value(org.hamcrest.Matchers.hasItem("إنما الأعمال بالنيات")))
                // ребро от топ-нарратора (collector, макс. position) в version-узел
                .andExpect(jsonPath("$.edges[?(@.target == '" + versionId + "')].source")
                        .value(org.hamcrest.Matchers.hasItem("narrator-" + collector.id())));
    }

    @Test
    void GET_sanadGraph_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/sanad-graph", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("hadith-not-found")));
    }

    @Test
    void GET_turuqGraph_mergesSiblings_sharedNarratorAndPerVersionEdges() throws Exception {
        Instant now = Instant.now();

        // Общий сподвижник между двумя версиями (тот же UUID шарится).
        Narrator companion = new Narrator(
                UUID.randomUUID(), null, "عمر بن الخطاب", "umar",
                null, null, null, 23, null, null, null,
                NarratorReliability.SAHABI, null, 0, null, now,
                "alminasa", "rawy-1", null, null, null, null);
        narratorRepository.save(companion);
        Narrator collMain = new Narrator(
                UUID.randomUUID(), null, "البخاري", "albukhari",
                null, null, null, 256, null, null, null,
                NarratorReliability.THIQA, null, 0, null, now,
                "alminasa", "rawy-2", null, null, null, null);
        narratorRepository.save(collMain);
        Narrator collSib = new Narrator(
                UUID.randomUUID(), null, "مسلم", "muslim",
                null, null, null, 261, null, null, null,
                NarratorReliability.THIQA, null, 0, null, now,
                "alminasa", "rawy-3", null, null, null, null);
        narratorRepository.save(collSib);

        // Главный хадис (146-1) + резолвленный сиблинг (158-2).
        Hadith main = new Hadith(
                UUID.randomUUID(), null, 1, "متن الأصل",
                HadithStatus.CANONICAL, null, null, now,
                "alminasa", "146-1", null, null, null, null);
        hadithRepository.save(main);
        Hadith sib = new Hadith(
                UUID.randomUUID(), null, 2, "متن الطريق",
                HadithStatus.VARIANT, null, null, now,
                "alminasa", "158-2", null, null, null, null);
        hadithRepository.save(sib);

        // main: companion → collMain ; sib: companion → collSib
        Sanad sMain = new Sanad(UUID.randomUUID(), main.id(), "SAHIH", null, null, true, null, now);
        sanadRepository.save(sMain);
        sanadRepository.saveNarratorLink(new SanadNarrator(sMain.id(), 0, companion.id(), "سمعت"));
        sanadRepository.saveNarratorLink(new SanadNarrator(sMain.id(), 1, collMain.id(), "حدثنا"));
        Sanad sSib = new Sanad(UUID.randomUUID(), sib.id(), "SAHIH", null, null, true, null, now);
        sanadRepository.save(sSib);
        sanadRepository.saveNarratorLink(new SanadNarrator(sSib.id(), 0, companion.id(), "عن"));
        sanadRepository.saveNarratorLink(new SanadNarrator(sSib.id(), 1, collSib.id(), "حدثنا"));

        // crossref главного → резолвленный сиблинг (related_hadith_id заполнен).
        crossrefRepository.save(new HadithCrossref(
                UUID.randomUUID(), main.id(), "158-2", sib.id(),
                "TARIQ", null, now));

        String vMain = "version-" + main.id();
        String vSib = "version-" + sib.id();

        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/turuq-graph", main.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hadithId").value(main.id().toString()))
                // общий узел сподвижника — ровно один
                .andExpect(jsonPath("$.nodes[?(@.id == 'narrator-" + companion.id() + "')].id")
                        .value(org.hamcrest.Matchers.hasSize(1)))
                // два version-узла
                .andExpect(jsonPath("$.nodes[?(@.role == 'VERSION')]")
                        .value(org.hamcrest.Matchers.hasSize(2)))
                // ребро prophet→companion агрегирует обе версии: sanadCount=2
                .andExpect(jsonPath("$.edges[?(@.source == 'prophet' && "
                        + "@.target == 'narrator-" + companion.id() + "')].data.sanadCount")
                        .value(org.hamcrest.Matchers.hasItem(2)))
                // у каждой версии своё ребро от своего топ-звена в свой version-узел
                .andExpect(jsonPath("$.edges[?(@.target == '" + vMain + "')].source")
                        .value(org.hamcrest.Matchers.hasItem("narrator-" + collMain.id())))
                .andExpect(jsonPath("$.edges[?(@.target == '" + vSib + "')].source")
                        .value(org.hamcrest.Matchers.hasItem("narrator-" + collSib.id())))
                // обе цепи в sanads[]
                .andExpect(jsonPath("$.sanads.length()").value(2));
    }

    @Test
    void GET_turuqGraph_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/hadith/hadiths/{id}/turuq-graph", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("hadith-not-found")));
    }
}
