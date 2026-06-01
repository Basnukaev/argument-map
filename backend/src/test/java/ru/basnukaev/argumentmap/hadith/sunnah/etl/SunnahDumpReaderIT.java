package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahBookRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahChapterRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahCollectionRow;
import ru.basnukaev.argumentmap.hadith.sunnah.etl.dto.SunnahHadithRow;

/**
 * IT для SunnahDumpReader против РЕАЛЬНОЙ MySQL-схемы дампа sunnah.com
 * (Collections/BookData/ChapterData/HadithTable, fixture
 * {@code sunnah/sample-schema.sql}). Phase 5 ETL шаг 2.d (ADR-052).
 * Plain Testcontainers (без Spring-контекста) — быстро.
 */
@Testcontainers
class SunnahDumpReaderIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("sunnah/sample-schema.sql");

    private static SunnahDumpReader reader;

    @BeforeAll
    static void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        ds.setDriverClassName(mysql.getDriverClassName());
        reader = new SunnahDumpReader(ds, new ObjectMapper());
    }

    @Test
    void reads_collections() {
        List<SunnahCollectionRow> collections = reader.readCollections();
        assertThat(collections).hasSize(1);
        SunnahCollectionRow c = collections.get(0);
        assertThat(c.name()).isEqualTo("bukhari");
        assertThat(c.hasBooks()).isTrue();
        assertThat(c.hasChapters()).isTrue();
        assertThat(c.totalHadith()).isEqualTo(7563);
        assertThat(c.totalAvailableHadith()).isEqualTo(7291);
        assertThat(c.titleAr()).isEqualTo("صحيح البخاري");
        assertThat(c.titleEn()).isEqualTo("Sahih al-Bukhari");
    }

    @Test
    void reads_books() {
        List<SunnahBookRow> books = reader.readBooks("bukhari");
        assertThat(books).hasSize(1);
        SunnahBookRow b = books.get(0);
        assertThat(b.collectionName()).isEqualTo("bukhari");
        assertThat(b.bookNumber()).isEqualTo("1");
        assertThat(b.nameAr()).isEqualTo("كتاب بدء الوحى");
        assertThat(b.nameEn()).isEqualTo("Revelation");
        assertThat(b.numberOfHadith()).isEqualTo(7);
    }

    @Test
    void reads_chapters_canonicalizing_fractional_bab_and_resolving_book_number() {
        Map<String, SunnahChapterRow> byId = index(reader.readChapters("bukhari"),
                SunnahChapterRow::chapterId);
        assertThat(byId.keySet()).containsExactlyInAnyOrder("1", "1.1");
        // book_number резолвится через JOIN с BookData (ChapterData хранит bookID)
        assertThat(byId.get("1").bookNumber()).isEqualTo("1");
        assertThat(byId.get("1").titleEn()).isEqualTo("How the Divine Revelation started");
        // дробный babID 1.1 НЕ схлопнулся в 1
        assertThat(byId.get("1.1").titleEn()).isEqualTo("Sub-chapter");
    }

    @Test
    void reads_hadiths_pairing_arabic_english_and_building_grades() {
        Map<String, SunnahHadithRow> byNum = index(reader.readHadiths("bukhari"),
                SunnahHadithRow::hadithNumber);
        assertThat(byNum.keySet()).containsExactlyInAnyOrder("1", "2");

        SunnahHadithRow h1 = byNum.get("1");
        assertThat(h1.bookNumber()).isEqualTo("1");
        assertThat(h1.chapterId()).isEqualTo("1");
        assertThat(h1.urnAr()).isEqualTo(100010L);
        assertThat(h1.urnEn()).isEqualTo(10L);
        assertThat(h1.bodyAr()).isEqualTo("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ");
        assertThat(h1.bodyEn()).isEqualTo("Actions are by intentions");
        assertThat(h1.gradesJson()).contains("Sahih");

        // hadith 2 ссылается на дробную главу 1.10 → канонический "1.1"
        assertThat(byNum.get("2").chapterId()).isEqualTo("1.1");
    }

    private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(java.util.stream.Collectors.toMap(key, Function.identity()));
    }
}
