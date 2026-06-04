package ru.basnukaev.argumentmap.hadith.alminasa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmExplanationRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmHadithRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmNarratorRow;
import ru.basnukaev.argumentmap.hadith.alminasa.etl.dto.AmRulingRow;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmExplanationStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmHadithStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmNarratorStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.repository.AmRulingStagingDao;
import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaImportSummary;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorRelation;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRelationRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;

/**
 * e2e IT оркестрации импорта alminasa staging→hd_* (план 3, Task 5): полный
 * прогон {@link AlminasaImportService#importAll} на фикстурах + resolve-проход
 * FK + failure-изоляция + идемпотентность.
 *
 * <p>Класс намеренно <b>БЕЗ</b> {@code @Transactional}: маппер-бины коммитят
 * каждый док в собственной транзакции, а resolve-проход и проверка
 * идемпотентности читают результаты ПОСЛЕ коммита — общая транзакция теста
 * скрыла бы это. Чистим staging + hd_* вручную в {@link #cleanup()}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AlminasaImportServiceIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private AmHadithStagingDao hadithStagingDao;
    @Autowired private AmNarratorStagingDao narratorStagingDao;
    @Autowired private AmRulingStagingDao rulingStagingDao;
    @Autowired private AmExplanationStagingDao explanationStagingDao;

    @Autowired private AlminasaImportService importService;
    @Autowired private HadithRepository hadithRepository;
    @Autowired private NarratorRepository narratorRepository;
    @Autowired private NarratorRelationRepository relationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/alminasa/" + name)) {
            return MAPPER.readTree(in);
        }
    }

    @AfterEach
    void cleanup() {
        // hd_* в FK-безопасном порядке (сателлиты → хадисы/рави/коллекции)
        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadith_editions");
        jdbcTemplate.update("DELETE FROM hd_hadith_crossrefs");
        jdbcTemplate.update("DELETE FROM hd_rulings");
        jdbcTemplate.update("DELETE FROM hd_explanations");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
        jdbcTemplate.update("DELETE FROM hd_narrator_relations");
        jdbcTemplate.update("DELETE FROM hd_narrators");
        jdbcTemplate.update("DELETE FROM hd_collections");
        // staging
        jdbcTemplate.update("DELETE FROM am_staging_explanation");
        jdbcTemplate.update("DELETE FROM am_staging_ruling");
        jdbcTemplate.update("DELETE FROM am_staging_narrator");
        jdbcTemplate.update("DELETE FROM am_staging_hadith");
    }

    /** Засев обоих хитов hadith-page.json (146-1, 146-53) + narrator 5719 + rulings + explanation 146-1. */
    private void seedFullFixtures() throws IOException {
        JsonNode hits = fixture("hadith-page.json").path("hits").path("hits");
        JsonNode src0 = hits.get(0).path("_source");
        JsonNode src1 = hits.get(1).path("_source");
        hadithStagingDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1L, "صحيح البخاري", "مرفوع",
                        "باب بدء الوحي", "باب كيف كان بدء الوحي إلى رسول الله", src0.toString()),
                new AmHadithRow("146-53", 146, 53L, "صحيح البخاري", "مرفوع",
                        "كتاب الإيمان", null, src1.toString())));

        JsonNode narratorSrc = fixture("narrators.json")
                .path("hits").path("hits").get(0).path("_source");
        narratorStagingDao.upsertAll(List.of(new AmNarratorRow(
                5719, "علقمة بن وقاص العتواري", "ثقة ثبت", "الثانية", narratorSrc.toString())));

        for (JsonNode hit : fixture("rulings.json").path("hits").path("hits")) {
            JsonNode rs = hit.path("_source");
            rulingStagingDao.upsertAll(List.of(new AmRulingRow(
                    hit.path("_id").asText(), rs.path("hadith_id").asText(),
                    rs.path("ruler").asText(), rs.path("ruler_dod").asInt(),
                    rs.path("narrations_type").asText(), rs.toString())));
        }

        for (JsonNode hit : fixture("explanations.json").path("hits").path("hits")) {
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
    void importAll_e2e_оба_хадиса_и_resolve_проход() throws IOException {
        seedFullFixtures();

        AlminasaImportSummary summary = importService.importAll();

        // ── counts ─────────────────────────────────────────────────────────────────
        assertThat(summary.narratorsProcessed()).isEqualTo(1);
        assertThat(summary.narratorsFailed()).isZero();
        assertThat(summary.hadithsProcessed()).isEqualTo(2);
        assertThat(summary.hadithsFailed()).isZero();
        assertThat(summary.failures()).isEmpty();

        // ── crossref 146-1 → 146-53 получил related_hadith_id ────────────────────────
        // 146-53 присутствует в raw_narrations 146-1 → crossref-строка существует;
        // оба хадиса импортированы → resolveCrossrefs проставил FK.
        Hadith h1 = hadithRepository.findByExternalId("alminasa", "146-1").orElseThrow();
        Hadith h53 = hadithRepository.findByExternalId("alminasa", "146-53").orElseThrow();
        assertThat(summary.crossrefsResolved()).isPositive();

        UUID resolvedFk = jdbcTemplate.queryForObject(
                "SELECT related_hadith_id FROM hd_hadith_crossrefs "
                        + "WHERE hadith_id = ? AND related_external_id = '146-53'",
                UUID.class, h1.id());
        assertThat(resolvedFk).isEqualTo(h53.id());

        // ── relation «الزهري» (короткая форма из top_students) остаётся NULL-FK ───────
        // known limitation решения 11б: full_name рави — полные формы, short form не матчится.
        Narrator n5719 = narratorRepository.findByExternalId("alminasa", "5719").orElseThrow();
        List<NarratorRelation> relations = relationRepository.findByNarratorId(n5719.id());
        assertThat(relations).anySatisfy(r -> {
            assertThat(r.relatedName()).isEqualTo("الزهري");
            assertThat(r.relatedNarratorId()).isNull();
        });
    }

    @Test
    void resolveNarratorRelations_позитивный_синтетический_точное_имя() throws IOException {
        // narrator A — точное полное имя; narrator B содержит A в top_students с тем же именем
        String targetName = "زيد بن ثابت الأنصاري النجاري";
        String rawA = "{\"full_name\":\"" + targetName + "\",\"grade\":\"ثقة\",\"level\":\"الأولى\"}";
        String rawB = "{\"full_name\":\"شيخ ب الفريد\",\"grade\":\"ثقة\",\"level\":\"الثالثة\","
                + "\"top_students\":[\"" + targetName + " - (5)\"]}";
        narratorStagingDao.upsertAll(List.of(
                new AmNarratorRow(900001, targetName, "ثقة", "الأولى", rawA),
                new AmNarratorRow(900002, "شيخ ب الفريد", "ثقة", "الثالثة", rawB)));

        importService.importNarrators();
        // resolveNarratorRelations отрабатывает в составе importHadiths (пустой staging хадисов)
        AlminasaImportSummary summary = importService.importHadiths(null);

        assertThat(summary.relationsResolved()).isEqualTo(1);

        Narrator a = narratorRepository.findByExternalId("alminasa", "900001").orElseThrow();
        Narrator b = narratorRepository.findByExternalId("alminasa", "900002").orElseThrow();
        List<NarratorRelation> relations = relationRepository.findByNarratorId(b.id());
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).relatedNarratorId()).isEqualTo(a.id());
    }

    @Test
    void importHadiths_failure_изоляция_битый_док_не_валит_прогон() throws IOException {
        // валидный 146-1 из фикстуры
        JsonNode src0 = fixture("hadith-page.json").path("hits").path("hits").get(0).path("_source");
        // битый: без matn_with_tashkeel → mapHadith бросает AlminasaMappingException
        String broken = "{\"hadith_id\":\"146-500\",\"book_name\":\"صحيح البخاري\","
                + "\"type\":\"مرفوع\",\"hadith\":\"نص بلا متن\",\"number\":[500]}";
        hadithStagingDao.upsertAll(List.of(
                new AmHadithRow("146-1", 146, 1L, "صحيح البخاري", "مرفوع",
                        "باب بدء الوحي", null, src0.toString()),
                new AmHadithRow("146-500", 146, 500L, "صحيح البخاري", "مرفوع", null, null, broken)));

        AlminasaImportSummary summary = importService.importHadiths(null);

        assertThat(summary.hadithsProcessed()).isEqualTo(1);
        assertThat(summary.hadithsFailed()).isEqualTo(1);
        assertThat(summary.failures()).hasSize(1);
        assertThat(summary.failures().get(0)).startsWith("hadith:146-500:");
        // здоровый док импортирован, битый — нет
        assertThat(hadithRepository.findByExternalId("alminasa", "146-1")).isPresent();
        assertThat(hadithRepository.findByExternalId("alminasa", "146-500")).isEmpty();
    }

    @Test
    void importAll_идемпотентен_row_counts_стабильны_uuid_стабилен() throws IOException {
        seedFullFixtures();

        importService.importAll();
        long[] before = hdRowCounts();
        UUID firstId = hadithRepository.findByExternalId("alminasa", "146-1").orElseThrow().id();

        importService.importAll();
        long[] after = hdRowCounts();
        UUID secondId = hadithRepository.findByExternalId("alminasa", "146-1").orElseThrow().id();

        assertThat(after).containsExactly(before);
        assertThat(secondId).isEqualTo(firstId);
    }

    /** Счётчики строк ключевых hd_*-таблиц для проверки идемпотентности. */
    private long[] hdRowCounts() {
        return new long[]{
                count("hd_hadiths"),
                count("hd_matns"),
                count("hd_sanads"),
                count("hd_rulings")
        };
    }

    private long count(String table) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return c == null ? 0L : c;
    }
}
