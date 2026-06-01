package ru.basnukaev.argumentmap.hadith.sunnah.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahBookDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahChapterDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahCollectionDao;
import ru.basnukaev.argumentmap.hadith.sunnah.repository.SunnahHadithDao;

/**
 * IT для SunnahToHadithMapper: staging sn_staging_* → hd_collections/hd_hadiths/
 * hd_matns. Phase 5 ETL шаг 2.c. Пилот: Бухари + Муслим (текст + grades +
 * структура книга/глава, без дедупа и без структурного иснада).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SunnahToHadithMapperIT {

    @Autowired
    private SunnahToHadithMapper mapper;

    @Autowired
    private SunnahCollectionDao collectionDao;

    @Autowired
    private SunnahBookDao bookDao;

    @Autowired
    private SunnahChapterDao chapterDao;

    @Autowired
    private SunnahHadithDao hadithDao;

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

    @BeforeEach
    void cleanup() {
        // DevHadithSeeder (@Profile local/dev) сеет hd_* при старте контекста —
        // чистим перед каждым тестом, чтобы ассертить на детерминированном состоянии
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
    }

    @Test
    void maps_hadith_creating_collection_hadith_and_primary_matn() throws Exception {
        seedBukhariStructure();
        hadithDao.upsertAll(List.of(new SunnahHadithRow(
                "bukhari", "1", "1", 1, 1L, 1L,
                "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "Actions are by intentions",
                "[{\"graded_by\":\"\",\"grade\":\"Sahih\"}]", null)));

        SunnahMappingResult result = mapper.mapCollection("bukhari");

        assertThat(result.inserted()).isEqualTo(1);

        Collection c = collectionRepository.findBySlug("bukhari").orElseThrow();
        assertThat(c.nameEn()).isEqualTo("Sahih al-Bukhari");
        assertThat(c.nameAr()).isEqualTo("صحيح البخاري");

        List<Hadith> hadiths = hadithRepository.findPage(null, null, c.id(), 10, 0);
        assertThat(hadiths).hasSize(1);
        Hadith h = hadiths.get(0);
        assertThat(h.primaryNumber()).isEqualTo(1);
        assertThat(h.normalizedMatn()).isEqualTo("انما الاعمال بالنيات");
        assertThat(h.status()).isEqualTo(HadithStatus.VARIANT);

        List<Matn> matns = matnRepository.findByHadithId(h.id());
        assertThat(matns).hasSize(1);
        Matn m = matns.get(0);
        assertThat(m.textAr()).isEqualTo("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ");
        assertThat(m.textEn()).isEqualTo("Actions are by intentions");
        assertThat(m.isPrimary()).isTrue();
        assertThat(m.printedNumber()).isEqualTo(1);
        assertThat(m.collectionId()).isEqualTo(c.id());

        // структура книга/глава едет в metadata matn'а (spec §6)
        JsonNode matnMeta = objectMapper.readTree(m.metadata());
        assertThat(matnMeta.get("bookNumber").asText()).isEqualTo("1");
        assertThat(matnMeta.get("chapterId").asInt()).isEqualTo(1);
    }

    @Test
    void maps_grades_into_metadata_in_frontend_expected_shape() throws Exception {
        seedBukhariStructure();
        hadithDao.upsertAll(List.of(new SunnahHadithRow(
                "bukhari", "1", "1", 1, null, null, "متن", "matn",
                "[{\"graded_by\":\"Al-Albani\",\"grade\":\"Sahih\"}]", null)));

        mapper.mapCollection("bukhari");

        Hadith h = hadithRepository.findPage(null, null, null, 10, 0).get(0);
        JsonNode grades = objectMapper.readTree(h.metadata()).get("grades");
        assertThat(grades.isArray()).isTrue();
        assertThat(grades).hasSize(1);
        // sunnah {graded_by} → {scholar}; контракт HadithController.parseGrades
        assertThat(grades.get(0).get("scholar").asText()).isEqualTo("Al-Albani");
        assertThat(grades.get(0).get("grade").asText()).isEqualTo("Sahih");
    }

    @Test
    void is_idempotent_on_second_run() {
        seedBukhariStructure();
        hadithDao.upsertAll(List.of(hadithRow("bukhari", "1", "متن واحد")));

        mapper.mapCollection("bukhari");
        SunnahMappingResult second = mapper.mapCollection("bukhari");

        assertThat(second.inserted()).isZero();
        assertThat(second.skippedExisting()).isEqualTo(1);
        assertThat(hadithRepository.countFiltered(null, null, null)).isEqualTo(1);
    }

    @Test
    void reuses_existing_collection_by_slug_without_overwriting() {
        UUID existingId = UUID.randomUUID();
        collectionRepository.save(new Collection(existingId, "bukhari",
                "صحيح البخاري", "Pre-existing", null, null, null, null, Instant.now()));
        seedBukhariStructure();
        hadithDao.upsertAll(List.of(hadithRow("bukhari", "1", "متن")));

        mapper.mapCollection("bukhari");

        // не создан дубль сборника, имя не перезаписано...
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hd_collections WHERE slug = 'bukhari'", Integer.class);
        assertThat(count).isEqualTo(1);
        assertThat(collectionRepository.findBySlug("bukhari").orElseThrow().nameEn())
                .isEqualTo("Pre-existing");
        // ...и хадис реально вставлен ИМЕННО в существующий сборник (не вакуумно)
        assertThat(hadithRepository.countFiltered(null, null, existingId)).isEqualTo(1);
    }

    @Test
    void skips_non_numeric_number_and_blank_arabic() {
        seedBukhariStructure();
        hadithDao.upsertAll(List.of(
                hadithRow("bukhari", "2", "نص صحيح"),
                hadithRow("bukhari", "2b", "نص بنومером غير числовым"),
                new SunnahHadithRow("bukhari", "3", "1", 1, null, null,
                        "   ", "only english", null, null)));

        SunnahMappingResult r = mapper.mapCollection("bukhari");

        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.skippedInvalid()).isEqualTo(2);
    }

    @Test
    void throws_when_staging_collection_missing() {
        assertThatThrownBy(() -> mapper.mapCollection("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private void seedBukhariStructure() {
        collectionDao.upsertAll(List.of(new SunnahCollectionRow(
                "bukhari", true, true, 7563, 7563,
                "صحيح البخاري", "Sahih al-Bukhari", null, null, null)));
        bookDao.upsertAll(List.of(new SunnahBookRow(
                "bukhari", "1", 1, 7, 7, "كتاب بدء الوحي", "Revelation", null)));
        chapterDao.upsertAll(List.of(new SunnahChapterRow(
                "bukhari", "1", 1, null, null,
                "باب كيف كان بدء الوحي", "How Revelation started",
                null, null, null, null, null)));
    }

    private static SunnahHadithRow hadithRow(String coll, String number, String bodyAr) {
        return new SunnahHadithRow(coll, number, "1", 1, null, null, bodyAr, null, null, null);
    }
}
