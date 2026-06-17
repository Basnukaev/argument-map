package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorCommentaryRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorCommentaryStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.AlminasaNarratorMapper.ParsedRelation;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;
import ru.basnukaev.argumentmap.hadith.domain.NarratorReliability;
import ru.basnukaev.argumentmap.hadith.repository.NarratorCommentaryRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRelationRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Unit-тесты {@link AlminasaNarratorMapper} — маппинг полей рави, производный
 * enum надёжности, хиджри-годы из прозы, разбор сети передатчиков (план 3,
 * Task 3). Mockito (репозитории замоканы), без Spring/Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class AlminasaNarratorMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private AmNarratorStagingDao narratorStagingDao;
    @Mock private AmNarratorCommentaryStagingDao narratorCommentaryStagingDao;
    @Mock private NarratorRepository narratorRepository;
    @Mock private NarratorRelationRepository relationRepository;
    @Mock private NarratorCommentaryRepository narratorCommentaryRepository;

    @Captor private ArgumentCaptor<Narrator> narratorCaptor;
    @Captor private ArgumentCaptor<NarratorRelation> relationCaptor;
    @Captor private ArgumentCaptor<NarratorCommentary> commentaryCaptor;

    private AlminasaNarratorMapper realMapper() {
        return new AlminasaNarratorMapper(narratorStagingDao, narratorCommentaryStagingDao,
                narratorRepository, relationRepository, narratorCommentaryRepository, MAPPER);
    }

    /** {@code _source} единственного хита narrators.json (рави 5719). */
    private static String narratorRawJson() throws IOException {
        try (InputStream in = AlminasaNarratorMapperTest.class
                .getResourceAsStream("/alminasa/narrators.json")) {
            JsonNode resp = MAPPER.readTree(in);
            return resp.path("hits").path("hits").get(0).path("_source").toString();
        }
    }

    private static String norm(String s) {
        return ArabicTextNormalizer.normalize(s);
    }

    @Test
    void mapNarrator_kunya_laqab_длиннее_120_усекаются() {
        // live-инцидент Сессии 57: nickname/origin живых доков > varchar(120)
        AlminasaNarratorMapper m = realMapper();
        String longText = "أبو يحيى ، وقيل : أبو واقد ، ".repeat(8); // > 120 символов
        String raw = "{\"full_name\":\"رجل\",\"nickname\":\"" + longText
                + "\",\"origin\":\"" + longText + "\"}";
        AmNarratorRow row = new AmNarratorRow(9100, "رجل", null, null, raw);
        when(narratorRepository.findByExternalId("alminasa", "9100")).thenReturn(Optional.empty());

        m.mapNarrator(row);

        verify(narratorRepository).save(narratorCaptor.capture());
        Narrator n = narratorCaptor.getValue();
        assertThat(n.kunya()).hasSizeLessThanOrEqualTo(120);
        assertThat(n.laqab()).hasSizeLessThanOrEqualTo(120);
    }

    @Test
    void mapNarrator_5719_маппит_все_поля() throws IOException {
        AlminasaNarratorMapper m = realMapper();
        AmNarratorRow row = new AmNarratorRow(
                5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", narratorRawJson());
        when(narratorRepository.findByExternalId("alminasa", "5719")).thenReturn(Optional.empty());

        m.mapNarrator(row);

        verify(narratorRepository).save(narratorCaptor.capture());
        Narrator n = narratorCaptor.getValue();
        assertThat(n.externalSource()).isEqualTo("alminasa");
        assertThat(n.externalId()).isEqualTo("5719");
        assertThat(n.nameAr()).isEqualTo("علقمة بن وقاص العتواري");
        assertThat(n.nameArNormalized()).isEqualTo(norm("علقمة بن وقاص العتواري"));
        // kunya = nickname «أبو يحيى ، وقيل : أبو واقد»
        assertThat(n.kunya()).isEqualTo("أبو يحيى ، وقيل : أبو واقد");
        // laqab = origin (нисба)
        assertThat(n.laqab()).isEqualTo("الليثي , العتواري , المدني");
        assertThat(n.tabaqa()).isEqualTo("الثانية");
        assertThat(n.gradeText()).isEqualTo("ثقة ثبت");
        assertThat(n.primaryResidence()).isEqualTo("المدينة"); // lived_in
        assertThat(n.deathPlace()).isEqualTo("المدينة");       // died_in
        // born_on/died_on — проза без числа → year* NULL
        assertThat(n.yearBirthHijri()).isNull();
        assertThat(n.yearDeathHijri()).isNull();
        assertThat(n.bornOnText()).isEqualTo("ولد على عهده عهد النبي صلى الله عليه وسلم");
        assertThat(n.diedOnText()).isEqualTo("في خلافة عبد الملك بن مروان");
        // tabaqa الثانية → reliability по grade (ثقة ثبت → THIQA)
        assertThat(n.reliabilityGrade()).isEqualTo(NarratorReliability.THIQA);
        // metadata: source + bookTitles[]
        JsonNode meta = MAPPER.readTree(n.metadata());
        assertThat(meta.path("source").asText()).isEqualTo("alminasa");
        assertThat(meta.path("bookTitles").isArray()).isTrue();
        assertThat(meta.path("bookTitles").size()).isEqualTo(11);
    }

    @Test
    void mapNarrator_5719_relations_из_top_students_и_scholars() throws IOException {
        AlminasaNarratorMapper m = realMapper();
        AmNarratorRow row = new AmNarratorRow(
                5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", narratorRawJson());
        when(narratorRepository.findByExternalId("alminasa", "5719")).thenReturn(Optional.empty());

        m.mapNarrator(row);

        verify(relationRepository, org.mockito.Mockito.times(6)).save(relationCaptor.capture());
        List<NarratorRelation> rels = relationCaptor.getAllValues();
        // 3 STUDENT + 3 SCHOLAR
        assertThat(rels).filteredOn(r -> r.role().equals("STUDENT")).hasSize(3);
        assertThat(rels).filteredOn(r -> r.role().equals("SCHOLAR")).hasSize(3);
        // «الزهري - (24)» → name=الزهري, cnt=24
        assertThat(rels).anySatisfy(r -> {
            assertThat(r.relatedName()).isEqualTo("الزهري");
            assertThat(r.cnt()).isEqualTo(24);
        });
        assertThat(rels).allSatisfy(r -> assertThat(r.relatedNarratorId()).isNull());
    }

    /**
     * Staged narrator-commentary (фикстура s59, рави 4396 Абу Хурайра) → строка
     * hd_narrator_commentaries: commenter, comments-вердикт, книга, год смерти
     * критика из sort-фоллбэка (852). delete-recreate в той же транзакции, что
     * mapNarrator.
     */
    @Test
    void mapNarrator_4396_строит_narrator_commentary_из_staging() throws IOException {
        AlminasaNarratorMapper m = realMapper();
        // raw цитаты = _source фикстуры с инжектированными id + commenter_dod
        // (как их положит парсер через fromNarratorCommentaryHit)
        AmNarratorCommentaryRow staged = commentaryRowFromFixture();
        AmNarratorRow row = new AmNarratorRow(
                4396, "أبو هريرة الدوسي", "الصحابي الجليل", "صحابي", "{\"full_name\":\"أبو هريرة الدوسي\"}");
        when(narratorRepository.findByExternalId("alminasa", "4396")).thenReturn(Optional.empty());
        when(narratorCommentaryStagingDao.findByNarratorId(4396)).thenReturn(List.of(staged));

        m.mapNarrator(row);

        // delete-recreate: сначала снос существующих цитат рави
        verify(narratorCommentaryRepository).deleteByNarratorId(org.mockito.ArgumentMatchers.any());
        verify(narratorCommentaryRepository).save(commentaryCaptor.capture());
        NarratorCommentary c = commentaryCaptor.getValue();
        assertThat(c.commenter()).isEqualTo("ابن حجر");
        assertThat(c.bookName()).isEqualTo("تقريب التهذيب");
        assertThat(c.author()).isEqualTo("ابن حجر العسقلاني");
        assertThat(c.page()).isEqualTo(1218);
        assertThat(c.volume()).isEqualTo(1);
        assertThat(c.comments()).containsExactly("الصحابي الجليل ، حافظ الصحابة");
        // год смерти критика — из sort[0] фоллбэка парсера (в _source фикстуры нет)
        assertThat(c.commenterDeathYear()).isEqualTo(852);
    }

    /**
     * Staging-row из _msearch-фикстуры через парсер (id + commenter_dod как в
     * live). Фикстура обёрнута в {@code response.responses[0].hits.hits[0]}.
     */
    private static AmNarratorCommentaryRow commentaryRowFromFixture() throws IOException {
        try (InputStream in = AlminasaNarratorMapperTest.class
                .getResourceAsStream("/alminasa/s59/narrator-commentary-12.json")) {
            JsonNode hit = MAPPER.readTree(in)
                    .path("response").path("responses").get(0).path("hits").path("hits").get(0);
            com.fasterxml.jackson.databind.node.ObjectNode src =
                    ((com.fasterxml.jackson.databind.node.ObjectNode) hit.path("_source")).put("id", "4396");
            return ru.basnukaev.argumentmap.hadith.alminasa.etl.AlminasaRows.fromNarratorCommentaryHit(
                    new ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit(
                            hit.path("_id").asText(), src, hit.path("sort")));
        }
    }

    @Test
    void mapNarrator_update_сохраняет_id_createdAt_счетчик() throws IOException {
        AlminasaNarratorMapper m = realMapper();
        java.util.UUID existingId = java.util.UUID.randomUUID();
        java.time.Instant created = java.time.Instant.parse("2020-01-01T00:00:00Z");
        Narrator existing = new Narrator(
                existingId, null, "علقمة", norm("علقمة"), null, null, null, null,
                null, null, null, NarratorReliability.THIQA, null, 42,
                "{\"source\":\"alminasa\"}", created, "alminasa", "5719", null, null, null, null);
        when(narratorRepository.findByExternalId("alminasa", "5719")).thenReturn(Optional.of(existing));
        AmNarratorRow row = new AmNarratorRow(
                5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", narratorRawJson());

        m.mapNarrator(row);

        verify(narratorRepository).update(narratorCaptor.capture());
        Narrator n = narratorCaptor.getValue();
        assertThat(n.id()).isEqualTo(existingId);
        assertThat(n.createdAt()).isEqualTo(created);
        assertThat(n.transmittedCountCached()).isEqualTo(42);
    }

    // ── tabaqa для SAHABI обнуляется (B4-фикс) ────────────────────────────────────

    @Test
    void mapNarrator_sahabi_tabaqa_null() {
        // level содержит почётный эпитет («الصحابي الجليل»), а не طبقة — должен быть null
        AlminasaNarratorMapper m = realMapper();
        String raw = "{\"full_name\":\"أبو هريرة الدوسي\","
                + "\"level\":\"الصحابي الجليل\","
                + "\"grade\":\"الصحابي الجليل  حافظ الصحابة\"}";
        AmNarratorRow row = new AmNarratorRow(4396, "أبو هريرة الدوسي", null, null, raw);
        when(narratorRepository.findByExternalId("alminasa", "4396")).thenReturn(Optional.empty());

        m.mapNarrator(row);

        verify(narratorRepository).save(narratorCaptor.capture());
        Narrator n = narratorCaptor.getValue();
        assertThat(n.reliabilityGrade()).isEqualTo(NarratorReliability.SAHABI);
        assertThat(n.tabaqa()).isNull();
    }

    @Test
    void mapNarrator_обычный_рави_tabaqa_сохранён() {
        // реальная طبقة («الثانية») у не-сподвижника остаётся как есть
        AlminasaNarratorMapper m = realMapper();
        String raw = "{\"full_name\":\"علقمة\","
                + "\"level\":\"الثانية\","
                + "\"grade\":\"ثقة ثبت\"}";
        AmNarratorRow row = new AmNarratorRow(9200, "علقمة", null, null, raw);
        when(narratorRepository.findByExternalId("alminasa", "9200")).thenReturn(Optional.empty());

        m.mapNarrator(row);

        verify(narratorRepository).save(narratorCaptor.capture());
        Narrator n = narratorCaptor.getValue();
        assertThat(n.reliabilityGrade()).isEqualTo(NarratorReliability.THIQA);
        assertThat(n.tabaqa()).isEqualTo("الثانية");
    }

    // ── таблица производного enum надёжности (решение 4) ──────────────────────────

    @Test
    void reliability_таблица_enum() {
        // level == صحابي → SAHABI (приоритет над grade)
        assertThat(AlminasaNarratorMapper.reliabilityGrade("صحابي", "أمير المؤمنين"))
                .isEqualTo(NarratorReliability.SAHABI);
        // префиксы grade
        assertThat(AlminasaNarratorMapper.reliabilityGrade("الثانية", "ثقة ثبت"))
                .isEqualTo(NarratorReliability.THIQA);
        assertThat(AlminasaNarratorMapper.reliabilityGrade("الثالثة", "صدوق يهم"))
                .isEqualTo(NarratorReliability.SADUQ);
        assertThat(AlminasaNarratorMapper.reliabilityGrade("الرابعة", "مقبول"))
                .isEqualTo(NarratorReliability.MAQBUL);
        assertThat(AlminasaNarratorMapper.reliabilityGrade("الخامسة", "ضعيف الحديث"))
                .isEqualTo(NarratorReliability.DAIF);
        assertThat(AlminasaNarratorMapper.reliabilityGrade("السادسة", "متروك"))
                .isEqualTo(NarratorReliability.MATRUK);
        // реальный кейс مالك: grade не начинается с известного префикса → UNKNOWN
        assertThat(AlminasaNarratorMapper.reliabilityGrade(
                "السابعة", "الفقيه  إمام دار الهجرة  رأس المتقنين"))
                .isEqualTo(NarratorReliability.UNKNOWN);
        // null grade → UNKNOWN
        assertThat(AlminasaNarratorMapper.reliabilityGrade("الأولى", null))
                .isEqualTo(NarratorReliability.UNKNOWN);

        // live-кейс Абу Хурайры (Сессия 58): level «الصحابي الجليل» ≠ строгое
        // «صحابي» — детекция по корню صحاب в level
        assertThat(AlminasaNarratorMapper.reliabilityGrade(
                "الصحابي الجليل", "الصحابي الجليل  حافظ الصحابة"))
                .isEqualTo(NarratorReliability.SAHABI);
        // сподвижница (женская форма)
        assertThat(AlminasaNarratorMapper.reliabilityGrade("صحابية", null))
                .isEqualTo(NarratorReliability.SAHABI);
        // level пуст, но gradeText НАЧИНАЕТСЯ с الصحابي → SAHABI
        assertThat(AlminasaNarratorMapper.reliabilityGrade(null, "الصحابي الجليل"))
                .isEqualTo(NarratorReliability.SAHABI);
        // табиин с упоминанием сподвижников В СЕРЕДИНЕ grade НЕ становится SAHABI
        assertThat(AlminasaNarratorMapper.reliabilityGrade("الثانية", "ثقة روى عن الصحابة"))
                .isEqualTo(NarratorReliability.THIQA);
    }

    // ── хиджри-год из прозы (решение 5) ───────────────────────────────────────────

    @Test
    void hijri_год_из_прозы() {
        // «سنة 94» → 94
        assertThat(AlminasaNarratorMapper.hijriYear("مات سنة 94 بالمدينة")).isEqualTo(94);
        // голое число
        assertThat(AlminasaNarratorMapper.hijriYear("توفي 256")).isEqualTo(256);
        // проза без числа → null
        assertThat(AlminasaNarratorMapper.hijriYear("في خلافة عبد الملك بن مروان")).isNull();
        assertThat(AlminasaNarratorMapper.hijriYear(null)).isNull();
        assertThat(AlminasaNarratorMapper.hijriYear("")).isNull();
    }

    // ── разбор сети передатчиков (решение, parse «имя - (N)») ─────────────────────

    @Test
    void relation_parse() {
        ParsedRelation simple = AlminasaNarratorMapper.parseRelation("الزهري - (24)");
        assertThat(simple.name()).isEqualTo("الزهري");
        assertThat(simple.cnt()).isEqualTo(24);

        // имя с дефисом внутри — N всё равно в конце в скобках
        ParsedRelation withDash = AlminasaNarratorMapper.parseRelation("عبد-الله بن أبي - (7)");
        assertThat(withDash.name()).isEqualTo("عبد-الله بن أبي");
        assertThat(withDash.cnt()).isEqualTo(7);

        // не распарсилось → имя целиком, cnt null
        ParsedRelation noCnt = AlminasaNarratorMapper.parseRelation("راو بдон счётчика");
        assertThat(noCnt.name()).isEqualTo("راو بдон счётчика");
        assertThat(noCnt.cnt()).isNull();
    }
}
