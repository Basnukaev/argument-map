package ru.basnukaev.argumentmap.hadith.sunnah.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;

/**
 * IT для staging-DAO sunnah.com ({@code sn_staging_*}). Заодно валидирует
 * migration 59 (Testcontainers применяет схему с нуля). Phase 5 ETL шаг 2.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SunnahStagingDaoIT {

    @Autowired
    private SunnahCollectionDao collectionDao;

    @Autowired
    private SunnahBookDao bookDao;

    @Autowired
    private SunnahChapterDao chapterDao;

    @Autowired
    private SunnahHadithDao hadithDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sn_staging_hadith");
        jdbcTemplate.update("DELETE FROM sn_staging_chapter");
        jdbcTemplate.update("DELETE FROM sn_staging_book");
        jdbcTemplate.update("DELETE FROM sn_staging_collection");
    }

    // --- collection ---

    @Test
    void collection_upsert_roundtrip_persists_all_fields() {
        collectionDao.upsertAll(List.of(new SunnahCollectionRow(
                "bukhari", true, true, 7563, 7563,
                "صحيح البخاري", "Sahih al-Bukhari",
                "مقدمة", "Intro", "{\"src\":\"dump\"}")));

        SunnahCollectionRow row = collectionDao.findByName("bukhari").orElseThrow();
        assertThat(row.name()).isEqualTo("bukhari");
        assertThat(row.hasBooks()).isTrue();
        assertThat(row.hasChapters()).isTrue();
        assertThat(row.totalHadith()).isEqualTo(7563);
        assertThat(row.totalAvailableHadith()).isEqualTo(7563);
        assertThat(row.titleAr()).isEqualTo("صحيح البخاري");
        assertThat(row.titleEn()).isEqualTo("Sahih al-Bukhari");
        assertThat(row.shortIntroAr()).isEqualTo("مقدمة");
        assertThat(row.rawJson()).contains("dump");
    }

    @Test
    void collection_upsert_is_idempotent_on_name() {
        collectionDao.upsertAll(List.of(collection("muslim", "Sahih Muslim v1")));
        collectionDao.upsertAll(List.of(collection("muslim", "Sahih Muslim v2")));

        assertThat(collectionDao.findByName("muslim").orElseThrow().titleEn())
                .isEqualTo("Sahih Muslim v2");
        assertThat(collectionDao.countAll()).isEqualTo(1);
        assertThat(collectionDao.findAll()).hasSize(1);
    }

    @Test
    void collection_handles_null_optional_fields() {
        collectionDao.upsertAll(List.of(new SunnahCollectionRow(
                "malik", null, null, null, null, null, null, null, null, null)));

        SunnahCollectionRow row = collectionDao.findByName("malik").orElseThrow();
        assertThat(row.hasBooks()).isNull();
        assertThat(row.totalHadith()).isNull();
        assertThat(row.titleAr()).isNull();
        assertThat(row.rawJson()).isNull();
    }

    // --- book ---

    @Test
    void book_upsert_roundtrip_with_fk_to_collection() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        bookDao.upsertAll(List.of(new SunnahBookRow(
                "bukhari", "1", 1, 7, 7, "كتاب بدء الوحي", "Revelation", "{}")));

        List<SunnahBookRow> books = bookDao.findByCollection("bukhari");
        assertThat(books).hasSize(1);
        assertThat(books.get(0).bookNumber()).isEqualTo("1");
        assertThat(books.get(0).nameAr()).isEqualTo("كتاب بدء الوحي");
        assertThat(books.get(0).nameEn()).isEqualTo("Revelation");
        assertThat(books.get(0).hadithStartNumber()).isEqualTo(1);
        assertThat(books.get(0).hadithEndNumber()).isEqualTo(7);
        assertThat(books.get(0).numberOfHadith()).isEqualTo(7);
    }

    @Test
    void book_upsert_with_invalid_collection_fk_throws() {
        assertThatThrownBy(() -> bookDao.upsertAll(List.of(new SunnahBookRow(
                "ghost", "1", null, null, null, null, null, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void book_accepts_non_numeric_book_number() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        bookDao.upsertAll(List.of(new SunnahBookRow(
                "bukhari", "introduction", null, null, null, null, "Introduction", null)));

        assertThat(bookDao.findByCollection("bukhari").get(0).bookNumber())
                .isEqualTo("introduction");
    }

    // --- chapter ---

    @Test
    void chapter_upsert_roundtrip() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        bookDao.upsertAll(List.of(new SunnahBookRow(
                "bukhari", "1", null, null, null, null, "Revelation", null)));
        chapterDao.upsertAll(List.of(new SunnahChapterRow(
                "bukhari", "1", "1", "١", "1", "باب", "Chapter",
                "مقدمة", "Intro", "خاتمة", "Ending", "{\"k\":1}")));

        // полное покрытие колонок: column-order mismatch в RowMapper иначе
        // незаметен при single-field assertions
        SunnahChapterRow ch = chapterDao.findByCollection("bukhari").get(0);
        assertThat(ch.chapterId()).isEqualTo("1");
        assertThat(ch.chapterNumberAr()).isEqualTo("١");
        assertThat(ch.chapterNumberEn()).isEqualTo("1");
        assertThat(ch.titleAr()).isEqualTo("باب");
        assertThat(ch.titleEn()).isEqualTo("Chapter");
        assertThat(ch.introAr()).isEqualTo("مقدمة");
        assertThat(ch.introEn()).isEqualTo("Intro");
        assertThat(ch.endingAr()).isEqualTo("خاتمة");
        assertThat(ch.endingEn()).isEqualTo("Ending");
        assertThat(ch.rawJson()).contains("\"k\"");
    }

    // --- hadith ---

    @Test
    void hadith_upsert_roundtrip_persists_body_and_grades_jsonb() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        hadithDao.upsertAll(List.of(new SunnahHadithRow(
                "bukhari", "1", "1", "1", 1L, 100001L,
                "إنما الأعمال بالنيات", "Actions are but by intentions",
                "[{\"graded_by\":\"\",\"grade\":\"Sahih\"}]", "{\"src\":\"dump\"}")));

        List<SunnahHadithRow> hadiths = hadithDao.findByCollection("bukhari");
        assertThat(hadiths).hasSize(1);
        SunnahHadithRow h = hadiths.get(0);
        assertThat(h.hadithNumber()).isEqualTo("1");
        assertThat(h.bodyAr()).isEqualTo("إنما الأعمال بالنيات");
        assertThat(h.bodyEn()).isEqualTo("Actions are but by intentions");
        assertThat(h.urnAr()).isEqualTo(1L);
        assertThat(h.urnEn()).isEqualTo(100001L);
        assertThat(h.gradesJson()).contains("Sahih");
    }

    @Test
    void hadith_upsert_is_idempotent_on_collection_and_number() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        hadithDao.upsertAll(List.of(hadith("bukhari", "1", "first")));
        hadithDao.upsertAll(List.of(hadith("bukhari", "1", "second")));

        List<SunnahHadithRow> hadiths = hadithDao.findByCollection("bukhari");
        assertThat(hadiths).hasSize(1);
        assertThat(hadiths.get(0).bodyAr()).isEqualTo("second");
        assertThat(hadithDao.countByCollection("bukhari")).isEqualTo(1);
    }

    @Test
    void hadith_handles_null_optional_fields() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        hadithDao.upsertAll(List.of(new SunnahHadithRow(
                "bukhari", "5", null, null, null, null, null, null, null, null)));

        List<SunnahHadithRow> hadiths = hadithDao.findByCollection("bukhari");
        assertThat(hadiths).hasSize(1);
        assertThat(hadiths.get(0).bookNumber()).isNull();
        assertThat(hadiths.get(0).chapterId()).isNull();
        assertThat(hadiths.get(0).gradesJson()).isNull();
    }

    // --- cascade + edge cases ---

    @Test
    void deleting_collection_cascades_to_books_chapters_hadiths() {
        collectionDao.upsertAll(List.of(collection("bukhari", "Sahih al-Bukhari")));
        bookDao.upsertAll(List.of(new SunnahBookRow(
                "bukhari", "1", null, null, null, null, "Revelation", null)));
        chapterDao.upsertAll(List.of(new SunnahChapterRow(
                "bukhari", "1", "1", null, null, null, "Chapter", null, null, null, null, null)));
        hadithDao.upsertAll(List.of(hadith("bukhari", "1", "matn")));

        // pre-state: дети существуют (иначе тест прошёл бы вакуумно даже
        // без работающего CASCADE — см. session insight по RED-прогону)
        assertThat(bookDao.countByCollection("bukhari")).isEqualTo(1);
        assertThat(chapterDao.countByCollection("bukhari")).isEqualTo(1);
        assertThat(hadithDao.countByCollection("bukhari")).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM sn_staging_collection WHERE name = 'bukhari'");

        assertThat(bookDao.countByCollection("bukhari")).isZero();
        assertThat(chapterDao.countByCollection("bukhari")).isZero();
        assertThat(hadithDao.countByCollection("bukhari")).isZero();
    }

    @Test
    void upsert_empty_list_returns_zero_for_all_daos() {
        assertThat(collectionDao.upsertAll(List.of())).isZero();
        assertThat(bookDao.upsertAll(List.of())).isZero();
        assertThat(chapterDao.upsertAll(List.of())).isZero();
        assertThat(hadithDao.upsertAll(List.of())).isZero();
    }

    // --- factories ---

    private static SunnahCollectionRow collection(String name, String titleEn) {
        return new SunnahCollectionRow(name, true, true, null, null,
                null, titleEn, null, null, null);
    }

    private static SunnahHadithRow hadith(String collection, String number, String bodyAr) {
        return new SunnahHadithRow(collection, number, null, null, null, null,
                bodyAr, null, null, null);
    }
}
