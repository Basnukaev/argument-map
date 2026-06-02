package ru.basnukaev.argumentmap.hadith.sunnah.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    void reads_collections_with_both_yesno_flag_branches() {
        Map<String, SunnahCollectionRow> byName = index(reader.readCollections(),
                SunnahCollectionRow::name);
        assertThat(byName.keySet()).containsExactlyInAnyOrder("bukhari", "muslim");

        SunnahCollectionRow bukhari = byName.get("bukhari");
        assertThat(bukhari.hasBooks()).isTrue();
        assertThat(bukhari.hasChapters()).isTrue();
        assertThat(bukhari.totalHadith()).isEqualTo(7563);
        assertThat(bukhari.totalAvailableHadith()).isEqualTo(7291);
        assertThat(bukhari.titleAr()).isEqualTo("صحيح البخاري");
        assertThat(bukhari.titleEn()).isEqualTo("Sahih al-Bukhari");
        // hasbooks='no' → false (вторая ветка yesNo)
        assertThat(byName.get("muslim").hasBooks()).isFalse();
    }

    @Test
    void reads_books_using_arabicBookNumber_not_bookID() {
        Map<String, SunnahBookRow> byNum = index(reader.readBooks("bukhari"),
                SunnahBookRow::bookNumber);
        assertThat(byNum.keySet()).containsExactlyInAnyOrder("1", "5");
        assertThat(byNum.get("1").nameEn()).isEqualTo("Revelation");
        // книга bookID=2.0 читается под bookNumber '5' (arabicBookNumber, не bookID)
        assertThat(byNum.get("5").nameEn()).isEqualTo("Belief");
        assertThat(byNum.get("5").nameAr()).isEqualTo("كتاب الإيمان");
        assertThat(byNum.get("1").numberOfHadith()).isEqualTo(7);
    }

    @Test
    void reads_chapters_resolving_book_number_via_join_and_canonicalizing_bab() {
        Map<String, SunnahChapterRow> byKey = reader.readChapters("bukhari").stream()
                .collect(Collectors.toMap(c -> c.bookNumber() + "/" + c.chapterId(),
                        Function.identity()));
        // JOIN: глава книги bookID=2.0 резолвится под bookNumber '5', НЕ '2'
        // (сломанный JOIN дал бы ключ "2/1" и тест бы упал)
        assertThat(byKey.keySet()).containsExactlyInAnyOrder("1/1", "1/1.1", "5/1");
        assertThat(byKey.get("1/1").titleEn()).isEqualTo("How the Divine Revelation started");
        // дробный babID не схлопнулся в "1"
        assertThat(byKey.get("1/1.1").titleEn()).isEqualTo("Sub-chapter");
        assertThat(byKey.get("5/1").titleEn()).isEqualTo("Belief Chapter");
    }

    @Test
    void reads_hadiths_pairing_arabic_english_grades_and_orphan_book_chapter() {
        Map<String, SunnahHadithRow> byNum = index(reader.readHadiths("bukhari"),
                SunnahHadithRow::hadithNumber);
        assertThat(byNum.keySet()).containsExactlyInAnyOrder("1", "2", "8", "3");

        SunnahHadithRow h1 = byNum.get("1");
        assertThat(h1.bookNumber()).isEqualTo("1");
        assertThat(h1.chapterId()).isEqualTo("1");
        assertThat(h1.urnAr()).isEqualTo(100010L);
        assertThat(h1.urnEn()).isEqualTo(10L);
        // fixture matn содержит markup (<c_q1>…</c_q1>, <p>) — reader срезает
        // её через SunnahTextCleaner, отдаёт чистый текст
        assertThat(h1.bodyAr()).isEqualTo("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ");
        assertThat(h1.bodyEn()).isEqualTo("Actions are by intentions");
        assertThat(h1.gradesJson()).contains("Sahih");

        // hadith 2 → дробная глава 1.10 → канонический "1.1"
        assertThat(byNum.get("2").chapterId()).isEqualTo("1.1");
        // hadith 8 → книга bookNumber '5'
        assertThat(byNum.get("8").bookNumber()).isEqualTo("5");
        // hadith 3 → orphan (bookNumber '99'), пустой grade → grades = null
        SunnahHadithRow h3 = byNum.get("3");
        assertThat(h3.bookNumber()).isEqualTo("99");
        assertThat(h3.chapterId()).isEqualTo("9");
        assertThat(h3.gradesJson()).isNull();
    }

    @Test
    void readHadithCounts_returns_actual_row_count_per_collection() {
        // Фикстура: 4 хадиса bukhari, 0 muslim → muslim отсутствует в результате
        Map<String, Integer> counts = reader.readHadithCounts();

        assertThat(counts).containsEntry("bukhari", 4);
        // muslim есть в Collections, но в HadithTable строк нет → не попадает в map
        assertThat(counts).doesNotContainKey("muslim");
    }

    private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
