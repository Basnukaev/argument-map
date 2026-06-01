package ru.basnukaev.argumentmap.hadith.sunnah.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.domain.Collection;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithStatus;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.repository.CollectionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.SunnahDumpReader;

/**
 * End-to-end IT пилотного конвейера Phase 5: MySQL-дамп sunnah.com → reader →
 * sn_staging_* → mapper → hd_*. Двухконтейнерный: Postgres (наша БД, через
 * {@link TestcontainersConfiguration}) + MySQL (дамп, fixture
 * {@code sunnah/sample-schema.sql}). Шаг 2.d (ADR-052).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers
class SunnahImportServiceIT {

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
    private MatnRepository matnRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SunnahDumpReader reader;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM hd_sanad_narrators");
        jdbcTemplate.update("DELETE FROM hd_sanads");
        jdbcTemplate.update("DELETE FROM hd_matns");
        jdbcTemplate.update("DELETE FROM hd_hadiths");
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
    void imports_bukhari_end_to_end_dump_to_hd() throws Exception {
        SunnahMappingResult result = importService.importCollection(reader, "bukhari");

        assertThat(result.inserted()).isEqualTo(4);

        Collection c = collectionRepository.findBySlug("bukhari").orElseThrow();
        assertThat(c.nameEn()).isEqualTo("Sahih al-Bukhari");
        assertThat(c.nameAr()).isEqualTo("صحيح البخاري");

        List<Hadith> hadiths = hadithRepository.findPage(null, null, c.id(), 10, 0);
        assertThat(hadiths).extracting(Hadith::primaryNumber)
                .containsExactlyInAnyOrder(1, 2, 8, 3);

        Hadith h1 = byNumber(hadiths, 1);
        assertThat(h1.status()).isEqualTo(HadithStatus.VARIANT);
        assertThat(h1.normalizedMatn()).isEqualTo("انما الاعمال بالنيات");

        // matn: english текст + обогащение book/chapter из BookData/ChapterData
        Matn m = matnRepository.findByHadithId(h1.id()).get(0);
        assertThat(m.textEn()).isEqualTo("Actions are by intentions");
        JsonNode matnMeta = objectMapper.readTree(m.metadata());
        assertThat(matnMeta.get("bookNameEn").asText()).isEqualTo("Revelation");
        assertThat(matnMeta.get("chapterTitleEn").asText())
                .isEqualTo("How the Divine Revelation started");

        // grades из dump ('Sahih') доехали в hd_hadiths.metadata
        JsonNode grades = objectMapper.readTree(h1.metadata()).get("grades");
        assertThat(grades.get(0).get("grade").asText()).isEqualTo("Sahih");

        // hadith 8 → книга bookNumber '5' (bookID 2.0): JOIN-доказательство
        // end-to-end. Сломанный ChapterData→BookData JOIN дал бы book_number
        // '2', chapterKey '5/1' не нашёлся бы → chapterTitle отсутствовал бы.
        JsonNode meta8 = objectMapper.readTree(matnOf(h(c, 8)).metadata());
        assertThat(meta8.get("bookNameEn").asText()).isEqualTo("Belief");
        assertThat(meta8.get("chapterTitleEn").asText()).isEqualTo("Belief Chapter");

        // hadith 3 → orphan (bookNumber '99' нет BookData, babID 9 нет
        // ChapterData) + пустой grade: импортируется, но без enrichment/grades
        Hadith h3 = byNumber(hadiths, 3);
        assertThat(objectMapper.readTree(h3.metadata()).has("grades")).isFalse();
        JsonNode meta3 = objectMapper.readTree(matnOf(h3).metadata());
        assertThat(meta3.get("bookNumber").asText()).isEqualTo("99");
        assertThat(meta3.has("bookNameEn")).isFalse();
        assertThat(meta3.get("chapterId").asText()).isEqualTo("9");
        assertThat(meta3.has("chapterTitleEn")).isFalse();
    }

    @Test
    void import_is_idempotent_across_reruns() {
        importService.importCollection(reader, "bukhari");
        SunnahMappingResult second = importService.importCollection(reader, "bukhari");

        assertThat(second.inserted()).isZero();
        assertThat(second.skippedExisting()).isEqualTo(4);
        assertThat(hadithRepository.countFiltered(null, null, null)).isEqualTo(4);
        // первичный matn не задублирован на повторном прогоне
        Collection c = collectionRepository.findBySlug("bukhari").orElseThrow();
        assertThat(matnRepository.findByHadithId(h(c, 1).id())).hasSize(1);
    }

    private Hadith h(Collection c, int primaryNumber) {
        return byNumber(hadithRepository.findPage(null, null, c.id(), 10, 0), primaryNumber);
    }

    private Matn matnOf(Hadith hadith) {
        return matnRepository.findByHadithId(hadith.id()).get(0);
    }

    private static Hadith byNumber(List<Hadith> hadiths, int primaryNumber) {
        return hadiths.stream()
                .filter(h -> Integer.valueOf(primaryNumber).equals(h.primaryNumber()))
                .findFirst().orElseThrow();
    }

    @Test
    void unknown_collection_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> importService.importCollection(reader, "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
