package ru.basnukaev.argumentmap.hadith.isnad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;
import ru.basnukaev.argumentmap.hadith.service.SanadGraphService;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDumpReader;
import ru.basnukaev.argumentmap.hadith.sunnah.service.SunnahImportService;
import ru.basnukaev.argumentmap.hadith.web.dto.SanadGraphResponse;

/**
 * End-to-end IT персиста извлечённого иснада (ADR-059 amendment): single-import
 * хадиса с включённым (fake) LLM → hd_sanads/hd_narrators/hd_sanad_narrators →
 * {@code SanadGraphService.buildGraph}.
 *
 * <p>Двухконтейнерный (как {@link SunnahImportService}-IT): Postgres (наша БД,
 * {@link TestcontainersConfiguration}) + MySQL (дамп sunnah,
 * {@code sunnah/sample-schema.sql}). {@link LlmClient} подменён fake'ом
 * (@Primary), отдающим каноничный isnad-JSON по содержимому матна — два хадиса
 * шарят нарратора (سفيان), проверяем cross-hadith дедуп.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, IsnadPersistenceIT.FakeLlmConfig.class})
@Testcontainers
class IsnadPersistenceIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("sunnah/sample-schema.sql");

    @Autowired
    private SunnahImportService importService;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private HadithRepository hadithRepository;

    @Autowired
    private NarratorRepository narratorRepository;

    @Autowired
    private SanadRepository sanadRepository;

    @Autowired
    private SanadGraphService sanadGraphService;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SunnahDumpReader reader;

    /**
     * Fake LLM с переключаемым enabled-флагом. По нормализованному матну
     * (tashkeel-нечувствительно) отдаёт каноничный isnad-JSON.
     *
     * <p>Хадис 1 («إنما الأعمال بالنيات»): цепь الحميدي → سفيان → عمر بن الخطاب.
     * Хадис 2 («حدثنا عبد الله»): цепь قتيبة → سفيان → أبو هريرة. Оба содержат
     * سفيان → cross-hadith дедуп: одна строка hd_narrators на обоих.
     */
    static final class SwitchableFakeLlm implements LlmClient {
        volatile boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            String norm = ArabicTextNormalizer.normalize(userPrompt);
            if (norm.contains(ArabicTextNormalizer.normalize("بالنيات"))) {
                return """
                        {"isnadFound": true,
                         "narrators": [
                           {"name": "الحميدي", "transmission": "حدثنا"},
                           {"name": "سفيان", "transmission": "حدثنا"},
                           {"name": "عمر بن الخطاب", "transmission": "عن النبي"}
                         ],
                         "cleanedMatn": "إنما الأعمال بالنيات"}""";
            }
            if (norm.contains(ArabicTextNormalizer.normalize("عبد الله"))) {
                return """
                        {"isnadFound": true,
                         "narrators": [
                           {"name": "قتيبة", "transmission": "حدثنا"},
                           {"name": "سفيان", "transmission": "حدثنا"},
                           {"name": "أبو هريرة", "transmission": "عن النبي"}
                         ],
                         "cleanedMatn": "حديث عبد الله"}""";
            }
            // прочие хадисы — иснад не выделяется
            return "{\"isnadFound\": false, \"narrators\": [], \"cleanedMatn\": null}";
        }
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlmClient() {
            return new SwitchableFakeLlm();
        }
    }

    @BeforeEach
    void setup() {
        ((SwitchableFakeLlm) llmClient).enabled = true;

        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
        jdbcTemplate.update("UPDATE hd_collections SET book_id = NULL");
        jdbcTemplate.update("DELETE FROM lib_books WHERE book_type = 'HADITH_COLLECTION'");
        jdbcTemplate.update("DELETE FROM hd_collections");
        jdbcTemplate.update("DELETE FROM hd_narrators");
        jdbcTemplate.update("DELETE FROM sn_staging_hadith");
        jdbcTemplate.update("DELETE FROM sn_staging_chapter");
        jdbcTemplate.update("DELETE FROM sn_staging_book");
        jdbcTemplate.update("DELETE FROM sn_staging_collection");

        DriverManagerDataSource ds = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        ds.setDriverClassName(mysql.getDriverClassName());
        reader = new SunnahDumpReader(ds, objectMapper);
    }

    @Test
    void singleImport_persistsIsnad_oneSanadPerHadith_companionAtPositionZero() {
        importService.importSingle(reader, "bukhari", "1");

        Hadith h1 = hadith("bukhari", 1);
        List<Sanad> sanads = sanadRepository.findByHadithId(h1.id());
        assertThat(sanads).hasSize(1);
        Sanad sanad = sanads.get(0);
        assertThat(sanad.primaryChain()).isTrue();
        // compiled_in_book_id = lib_books-представление сборника
        Collection c = collectionRepository.findBySlug("bukhari").orElseThrow();
        assertThat(sanad.compiledInBookId()).isEqualTo(c.bookId());

        List<SanadNarrator> links = sanadRepository.findNarratorsBySanadId(sanad.id());
        assertThat(links).hasSize(3);
        // position 0 = сподвижник (Prophet-side) = последний в извлечённой цепи
        Narrator pos0 = narratorRepository.findById(links.get(0).narratorId()).orElseThrow();
        assertThat(pos0.nameAr()).isEqualTo("عمر بن الخطاب");
        // верх цепи = составительский источник الحميدي на максимальной позиции
        Narrator posTop = narratorRepository.findById(links.get(2).narratorId()).orElseThrow();
        assertThat(posTop.nameAr()).isEqualTo("الحميدي");
        // transmission сподвижника = формула к Пророку
        assertThat(links.get(0).transmissionPhrase()).isEqualTo("عن النبي");
    }

    @Test
    void crossHadithDedup_sharedNarratorName_singleRowReferencedByBoth() {
        importService.importSingle(reader, "bukhari", "1");
        importService.importSingle(reader, "bukhari", "2");

        // سفيان встречается в обеих цепях → ровно одна строка hd_narrators
        String sufyanNorm = ArabicTextNormalizer.normalize("سفيان");
        Long sufyanRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hd_narrators WHERE name_ar_normalized = ?",
                Long.class, sufyanNorm);
        assertThat(sufyanRows).isEqualTo(1L);

        UUID sufyanId = narratorRepository.findByNameArNormalized(sufyanNorm).orElseThrow().id();

        // та же строка нарратора в обеих цепях
        Hadith h1 = hadith("bukhari", 1);
        Hadith h2 = hadith("bukhari", 2);
        UUID s1 = sanadRepository.findByHadithId(h1.id()).get(0).id();
        UUID s2 = sanadRepository.findByHadithId(h2.id()).get(0).id();
        assertThat(narratorIdsOf(s1)).contains(sufyanId);
        assertThat(narratorIdsOf(s2)).contains(sufyanId);

        // всего уникальных нарраторов: الحميدي, سفيان, عمر, قتيبة, أبو هريرة = 5
        Long totalNarrators = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hd_narrators", Long.class);
        assertThat(totalNarrators).isEqualTo(5L);
    }

    @Test
    void buildGraph_returnsProphetRootAndNarrators_endToEnd() {
        importService.importSingle(reader, "bukhari", "1");
        Hadith h1 = hadith("bukhari", 1);

        SanadGraphResponse graph = sanadGraphService.buildGraph(h1.id());

        // синтетический корень Пророка ﷺ + 3 нарратора
        assertThat(graph.nodes()).anyMatch(n -> n.role().equals("PROPHET"));
        assertThat(graph.nodes()).anyMatch(n ->
                n.role().equals("COMPANION") && "عمر بن الخطاب".equals(n.data().nameAr()));
        assertThat(graph.nodes()).filteredOn(n -> n.role().equals("NARRATOR")).hasSize(2);
        // 1 цепь в сводке
        assertThat(graph.sanads()).hasSize(1);
        // рёбра: prophet→companion + 2 внутренних = 3
        assertThat(graph.edges()).hasSize(3);
    }

    @Test
    void reimport_sameHadith_isIdempotent_noDuplicateSanad() {
        importService.importSingle(reader, "bukhari", "1");
        Hadith h1 = hadith("bukhari", 1);
        UUID firstSanadId = sanadRepository.findByHadithId(h1.id()).get(0).id();

        importService.importSingle(reader, "bukhari", "1");

        List<Sanad> sanads = sanadRepository.findByHadithId(h1.id());
        assertThat(sanads).hasSize(1);
        // delete-recreate: новая строка цепи (id отличается), но ровно одна
        assertThat(sanads.get(0).id()).isNotEqualTo(firstSanadId);
        assertThat(sanadRepository.findNarratorsBySanadId(sanads.get(0).id())).hasSize(3);
    }

    @Test
    void llmDisabled_importSucceeds_noSanadCreated() {
        ((SwitchableFakeLlm) llmClient).enabled = false;

        importService.importSingle(reader, "bukhari", "1");

        Hadith h1 = hadith("bukhari", 1);
        assertThat(hadithRepository.findById(h1.id())).isPresent();
        assertThat(sanadRepository.findByHadithId(h1.id())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hd_narrators", Long.class)).isZero();
    }

    @Test
    void bulkImport_extractIsnadTrue_persistsForEachHadith() {
        importService.importCollection(reader, "bukhari", true);

        // хадисы 1 и 2 дают иснад; 8 и 3 — fake возвращает isnadFound=false
        Hadith h1 = hadith("bukhari", 1);
        Hadith h2 = hadith("bukhari", 2);
        Hadith h8 = hadith("bukhari", 8);
        assertThat(sanadRepository.findByHadithId(h1.id())).hasSize(1);
        assertThat(sanadRepository.findByHadithId(h2.id())).hasSize(1);
        assertThat(sanadRepository.findByHadithId(h8.id())).isEmpty();
    }

    @Test
    void bulkImport_extractIsnadFalse_noSanadCreated() {
        importService.importCollection(reader, "bukhari", false);

        Hadith h1 = hadith("bukhari", 1);
        assertThat(sanadRepository.findByHadithId(h1.id())).isEmpty();
    }

    private Hadith hadith(String slug, int number) {
        Collection c = collectionRepository.findBySlug(slug).orElseThrow();
        return hadithRepository.findByCollectionIdAndPrimaryNumber(c.id(), number).orElseThrow();
    }

    private List<UUID> narratorIdsOf(UUID sanadId) {
        return sanadRepository.findNarratorsBySanadId(sanadId).stream()
                .map(SanadNarrator::narratorId)
                .toList();
    }
}
